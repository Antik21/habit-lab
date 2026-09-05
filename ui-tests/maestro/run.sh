#!/usr/bin/env bash

set -Eeuo pipefail

readonly EXPECTED_MAESTRO_VERSION="2.6.1"
readonly APP_ID="com.denis.habitlab"
readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly REPOSITORY_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
readonly FLOW_FILE="$SCRIPT_DIR/flows/reference-screens.yaml"
readonly CONFIG_FILE="$SCRIPT_DIR/config.yaml"
readonly XCODE_PREFLIGHT="$SCRIPT_DIR/xcode-preflight.sh"

usage() {
    printf 'Usage: %s android|ios <device-id> [run-id]\n' "$0" >&2
}

fail() {
    printf 'error: %s\n' "$1" >&2
    exit 2
}

if (( $# < 2 || $# > 3 )); then
    usage
    exit 2
fi

readonly PLATFORM="$1"
readonly DEVICE_ID="$2"
readonly RUN_ID="${3:-$(date -u '+%Y%m%dT%H%M%SZ')}"

case "$PLATFORM" in
    android|ios) ;;
    *) fail "platform must be exactly 'android' or 'ios'" ;;
esac

[[ -n "$DEVICE_ID" ]] || fail "device-id must not be empty"
[[ "$RUN_ID" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]] ||
    fail "run-id must be 1-64 ASCII letters, digits, dots, underscores, or hyphens and start with an alphanumeric"

if [[ "$PLATFORM" == "android" ]]; then
    [[ "$DEVICE_ID" =~ ^emulator-[0-9]+$ ]] ||
        fail "Android device-id must identify an emulator (for example, emulator-5554)"
else
    [[ "$DEVICE_ID" =~ ^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$ ]] ||
        fail "iOS device-id must be an explicit simulator UDID"
fi

if [[ -x /usr/libexec/java_home ]]; then
    jbr_21_home="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    if [[ -n "$jbr_21_home" && -x "$jbr_21_home/bin/java" ]]; then
        export JAVA_HOME="$jbr_21_home"
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
fi

command -v java >/dev/null 2>&1 || fail "Java 17 or newer is required by Maestro"
java_version="$(java -version 2>&1 | sed -n '1s/.*version "\([^"]*\)".*/\1/p')"
java_major="${java_version%%.*}"
if [[ "$java_major" == "1" ]]; then
    java_major="$(printf '%s' "$java_version" | cut -d. -f2)"
fi
[[ "$java_major" =~ ^[0-9]+$ ]] || fail "could not determine the Java major version"
(( java_major >= 17 )) || fail "Java 17 or newer is required by Maestro"

maestro_candidate="$(command -v maestro || true)"
[[ -n "$maestro_candidate" && -x "$maestro_candidate" ]] ||
    fail "Maestro $EXPECTED_MAESTRO_VERSION is not installed or not on PATH"
maestro_parent="$(cd -P -- "$(dirname -- "$maestro_candidate")" 2>/dev/null && pwd -P)" ||
    fail "could not canonicalize the Maestro executable path"
readonly MAESTRO_BIN="$maestro_parent/$(basename -- "$maestro_candidate")"
[[ -x "$MAESTRO_BIN" ]] || fail "the canonical Maestro executable is unavailable"
maestro_version="$($MAESTRO_BIN --version 2>/dev/null | tr -d '\r\n')"
[[ "$maestro_version" == "$EXPECTED_MAESTRO_VERSION" ]] ||
    fail "Maestro $EXPECTED_MAESTRO_VERSION is required; found '${maestro_version:-unknown}'"

readonly ARTIFACT_DIR="$REPOSITORY_ROOT/build/maestro/$RUN_ID/$PLATFORM"
readonly COMMAND_LOG="$ARTIFACT_DIR/command.log"
readonly REPORT_FILE="$ARTIFACT_DIR/report.xml"
readonly SCREENSHOT_DIR="$ARTIFACT_DIR/screenshots"
readonly DEBUG_DIR="$ARTIFACT_DIR/debug"
[[ ! -e "$ARTIFACT_DIR" ]] ||
    fail "artifact directory already exists; choose a unique run-id: $ARTIFACT_DIR"
mkdir -p "$(dirname -- "$ARTIFACT_DIR")"
mkdir "$ARTIFACT_DIR"
mkdir "$SCREENSHOT_DIR" "$DEBUG_DIR"

set +e
(
    set -Eeuo pipefail
    cd "$REPOSITORY_ROOT"

    run_maestro() {
        "$MAESTRO_BIN" "$@"
    }

    printf 'platform=%s\ndevice=%s\nrun_id=%s\nmaestro=%s\njava_home=%s\n' \
        "$PLATFORM" "$DEVICE_ID" "$RUN_ID" "$maestro_version" "${JAVA_HOME:-}"

    if [[ "$PLATFORM" == "android" ]]; then
        command -v adb >/dev/null 2>&1 || fail "adb is required for the Android runner"
        adb_state="$(adb -s "$DEVICE_ID" get-state 2>/dev/null || true)"
        [[ "$adb_state" == "device" ]] || fail "Android emulator '$DEVICE_ID' is not connected and ready"
        [[ "$(adb -s "$DEVICE_ID" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')" == "1" ]] ||
            fail "Android target '$DEVICE_ID' is not an emulator"

        ./gradlew :androidApp:assembleDebug
        readonly apk_path="$REPOSITORY_ROOT/androidApp/build/outputs/apk/debug/androidApp-debug.apk"
        [[ -f "$apk_path" ]] || fail "Android debug APK was not produced at $apk_path"
        adb -s "$DEVICE_ID" install -r "$apk_path"
    else
        # shellcheck source=xcode-preflight.sh
        source "$XCODE_PREFLIGHT"
        habitlab_xcode_preflight
        printf 'xcode_version=%s\n' "$HABITLAB_XCODE_VERSION"
        pinned_xcrun() {
            habitlab_xcode_run_xcrun "$@"
        }
        pinned_xcodebuild() {
            habitlab_xcode_run_xcodebuild "$@"
        }
        run_maestro() {
            habitlab_xcode_run_external "$MAESTRO_BIN" "$@"
        }

        pinned_xcrun simctl list devices available | grep -F "($DEVICE_ID)" >/dev/null ||
            fail "iOS simulator '$DEVICE_ID' is not available"
        pinned_xcrun simctl bootstatus "$DEVICE_ID" -b
        simulator_model="$(pinned_xcrun simctl getenv "$DEVICE_ID" SIMULATOR_MODEL_IDENTIFIER)"
        [[ "$simulator_model" == iPhone* || "$simulator_model" == iPad* ]] ||
            fail "iOS target '$DEVICE_ID' is not an iPhone or iPad simulator"

        readonly derived_data="$ARTIFACT_DIR/DerivedData"
        readonly simulator_arch="$(uname -m)"
        pinned_xcodebuild \
            -project iosApp/iosApp.xcodeproj \
            -scheme iosApp \
            -configuration Debug \
            -sdk iphonesimulator \
            -derivedDataPath "$derived_data" \
            CODE_SIGNING_ALLOWED=NO \
            ONLY_ACTIVE_ARCH=YES \
            ARCHS="$simulator_arch" \
            build
        readonly app_path="$derived_data/Build/Products/Debug-iphonesimulator/iosApp.app"
        [[ -d "$app_path" ]] || fail "iOS simulator app was not produced at $app_path"
        habitlab_xcode_try_xcrun simctl terminate "$DEVICE_ID" "$APP_ID" >/dev/null
        pinned_xcrun simctl install "$DEVICE_ID" "$app_path"
    fi

    run_maestro test \
        --platform "$PLATFORM" \
        --udid "$DEVICE_ID" \
        --config "$CONFIG_FILE" \
        --format JUNIT \
        --output "$REPORT_FILE" \
        --test-output-dir "$ARTIFACT_DIR" \
        --debug-output "$DEBUG_DIR" \
        --flatten-debug-output \
        --no-ansi \
        -e "RUN_ID=$RUN_ID" \
        "$FLOW_FILE"

    [[ -s "$REPORT_FILE" ]] || fail "Maestro did not produce a non-empty JUnit report"
    grep -Eq '<testsuite[^>]*tests="1"[^>]*failures="0"' "$REPORT_FILE" ||
        fail "Maestro JUnit report does not declare one passing flow with zero failures"
    grep -Eq '<testcase[^>]*status="SUCCESS"' "$REPORT_FILE" ||
        fail "Maestro JUnit report does not contain a successful test case"
    if grep -Eq '<failure([[:space:]>])|<error([[:space:]>])' "$REPORT_FILE"; then
        fail "Maestro JUnit report contains a failure or error element"
    fi

    screenshot_count="$(find "$SCREENSHOT_DIR" -type f -name '*.png' | wc -l | tr -d '[:space:]')"
    [[ "$screenshot_count" =~ ^[0-9]+$ ]] || fail "could not count Maestro screenshots"
    (( screenshot_count >= 3 )) ||
        fail "Maestro produced $screenshot_count screenshots; at least 3 are required"

    [[ -s "$DEBUG_DIR/maestro.log" ]] || fail "Maestro did not produce a non-empty debug log"
    [[ -s "$DEBUG_DIR/commands-(reference-screens).json" ]] ||
        fail "Maestro did not produce a non-empty command trace"
) 2>&1 | tee "$COMMAND_LOG"
pipeline_status=("${PIPESTATUS[@]}")
set -e

if (( pipeline_status[0] != 0 )); then
    exit "${pipeline_status[0]}"
fi
if (( pipeline_status[1] != 0 )); then
    printf 'error: tee failed while writing %s\n' "$COMMAND_LOG" >&2
    exit "${pipeline_status[1]}"
fi
[[ -s "$COMMAND_LOG" ]] || fail "runner did not produce a non-empty command log"
