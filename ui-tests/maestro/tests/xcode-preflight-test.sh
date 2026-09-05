#!/usr/bin/env bash

set -Eeuo pipefail

readonly TEST_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly MAESTRO_DIR="$(cd -- "$TEST_DIR/.." && pwd)"
readonly REPOSITORY_ROOT="$(cd -- "$MAESTRO_DIR/../.." && pwd)"
readonly PREFLIGHT="$MAESTRO_DIR/xcode-preflight.sh"
readonly RUNNER="$MAESTRO_DIR/run.sh"
readonly TEST_ROOT="$(mktemp -d)"
readonly NEGATIVE_RUN_ID="xcode-validation-failure-$$-$RANDOM"
readonly NEGATIVE_ARTIFACT_ROOT="$REPOSITORY_ROOT/build/maestro/$NEGATIVE_RUN_ID"
readonly HAPPY_RUN_ID="xcode-happy-path-$$-$RANDOM"
readonly HAPPY_ARTIFACT_ROOT="$REPOSITORY_ROOT/build/maestro/$HAPPY_RUN_ID"

delete_tree() {
    local path="$1"

    if [[ -d "$path" ]]; then
        find "$path" -depth -delete
    fi
}

cleanup() {
    delete_tree "$TEST_ROOT"
    delete_tree "$NEGATIVE_ARTIFACT_ROOT"
    delete_tree "$HAPPY_ARTIFACT_ROOT"
}
trap cleanup EXIT

fail_test() {
    printf 'FAIL: %s\n' "$1" >&2
    exit 1
}

create_case() {
    local name="$1"

    CASE_DIR="$TEST_ROOT/$name"
    STUB_DIR="$CASE_DIR/stubs"
    DEVELOPER_ROOT="$CASE_DIR/Xcode.app/Contents/Developer"
    XCODEBUILD_BIN="$DEVELOPER_ROOT/usr/bin/xcodebuild"
    mkdir -p "$STUB_DIR" "$(dirname -- "$XCODEBUILD_BIN")"

    cp "$TEST_DIR/stubs/xcode-select" "$STUB_DIR/xcode-select"
    cp "$TEST_DIR/stubs/xcrun" "$STUB_DIR/xcrun"
    cp "$TEST_DIR/stubs/java" "$STUB_DIR/java"
    cp "$TEST_DIR/stubs/maestro" "$STUB_DIR/maestro"
    cp "$TEST_DIR/stubs/xcodebuild" "$XCODEBUILD_BIN"
    chmod +x "$STUB_DIR"/* "$XCODEBUILD_BIN"
    CANONICAL_DEVELOPER_ROOT="$(cd -P -- "$DEVELOPER_ROOT" && pwd -P)"
    export HABITLAB_XCODE_PREFLIGHT_TEST_MODE=1
    export HABITLAB_XCODE_PREFLIGHT_TEST_XCRUN="$STUB_DIR/xcrun"
    export HABITLAB_XCODE_PREFLIGHT_TEST_XCODE_SELECT="$STUB_DIR/xcode-select"
}

run_standalone() {
    env \
        DEVELOPER_DIR= \
        PATH="$STUB_DIR:$PATH" \
        STUB_DEVELOPER_DIR="$DEVELOPER_ROOT" \
        STUB_XCODEBUILD="$XCODEBUILD_BIN" \
        STUB_XCODE_VERSION="$1" \
        "$PREFLIGHT"
}

if collision_output="$(env HABITLAB_MIN_XCODE_MAJOR=999 "$PREFLIGHT" 2>&1)"; then
    fail_test "standalone variable collision was accepted"
fi
[[ "$collision_output" == *"symbol collision"* ]] ||
    fail_test "standalone variable collision was not reported"

if collision_output="$(bash -c \
    'HABITLAB_XCODE_VERSION_LIBRARY=occupied; source "$1"' \
    _ "$PREFLIGHT" 2>&1)"; then
    fail_test "non-strict variable collision was accepted"
fi
[[ "$collision_output" == *"symbol collision"* ]] ||
    fail_test "non-strict variable collision was not reported"

if collision_output="$(bash -Eeuo pipefail -c \
    'HABITLAB_XCODEBUILD_BIN=occupied; source "$1"' \
    _ "$PREFLIGHT" 2>&1)"; then
    fail_test "strict variable collision was accepted"
fi
[[ "$collision_output" == *"symbol collision"* ]] ||
    fail_test "strict variable collision was not reported"

if collision_output="$(bash -c \
    'habitlab_read_xcode_version() { :; }; source "$1"' \
    _ "$PREFLIGHT" 2>&1)"; then
    fail_test "non-strict function collision was accepted"
fi
[[ "$collision_output" == *"symbol collision"* ]] ||
    fail_test "non-strict function collision was not reported"

if collision_output="$(bash -Eeuo pipefail -c \
    'habitlab_xcode_preflight() { :; }; source "$1"' \
    _ "$PREFLIGHT" 2>&1)"; then
    fail_test "strict function collision was accepted"
fi
[[ "$collision_output" == *"symbol collision"* ]] ||
    fail_test "strict function collision was not reported"

if collision_output="$(bash -c \
    'source "$1" || exit; source "$1"' \
    _ "$PREFLIGHT" 2>&1)"; then
    fail_test "non-strict repeated source was accepted"
fi
[[ "$collision_output" == *"symbol collision"* ]] ||
    fail_test "non-strict repeated source was not reported"

if collision_output="$(bash -Eeuo pipefail -c \
    'source "$1"; source "$1"' \
    _ "$PREFLIGHT" 2>&1)"; then
    fail_test "strict repeated source was accepted"
fi
[[ "$collision_output" == *"symbol collision"* ]] ||
    fail_test "strict repeated source was not reported"

create_case supported
production_resolution="$({
    unset HABITLAB_XCODE_PREFLIGHT_TEST_MODE
    unset HABITLAB_XCODE_PREFLIGHT_TEST_XCRUN
    unset HABITLAB_XCODE_PREFLIGHT_TEST_XCODE_SELECT
    export PATH="$STUB_DIR:$PATH"
    source "$PREFLIGHT"
    [[ "$HABITLAB_SYSTEM_XCRUN" == /usr/bin/xcrun ]] || exit 1
    [[ "$HABITLAB_SYSTEM_XCODE_SELECT" == /usr/bin/xcode-select ]] || exit 1
    if [[ -x /usr/bin/xcrun ]]; then
        [[ "$(habitlab_resolve_xcrun)" == /usr/bin/xcrun ]] || exit 1
    elif habitlab_resolve_xcrun >/dev/null 2>&1; then
        exit 1
    fi
    if [[ -x /usr/bin/xcode-select ]]; then
        [[ "$(habitlab_resolve_xcode_select)" == /usr/bin/xcode-select ]] || exit 1
    elif habitlab_resolve_xcode_select >/dev/null 2>&1; then
        exit 1
    fi
} 2>&1)" || fail_test "production selector resolution used PATH-prepended tools"
[[ -z "$production_resolution" ]] || fail_test "production selector resolution was noisy"

if injection_output="$({
    unset HABITLAB_XCODE_PREFLIGHT_TEST_MODE
    export HABITLAB_XCODE_PREFLIGHT_TEST_XCRUN="$STUB_DIR/xcrun"
    export HABITLAB_XCODE_PREFLIGHT_TEST_XCODE_SELECT="$STUB_DIR/xcode-select"
    source "$PREFLIGHT"
    habitlab_resolve_xcrun
} 2>&1)"; then
    fail_test "test dependency injection worked without explicit test mode"
fi
[[ "$injection_output" == *"requires explicit test mode"* ]] ||
    fail_test "implicit test dependency injection failure was not reported"

supported_output="$(run_standalone 26.4)" || fail_test "supported Xcode was rejected"
[[ "$supported_output" == "Xcode preflight passed: selected Xcode 26.4" ]] ||
    fail_test "standalone success was not concise"
pinned_output="$(
    export PATH="$STUB_DIR:$PATH"
    export DEVELOPER_DIR=
    export STUB_DEVELOPER_DIR="$DEVELOPER_ROOT"
    export STUB_XCODEBUILD="$XCODEBUILD_BIN"
    export STUB_XCODE_VERSION=26.4
    # shellcheck source=../xcode-preflight.sh
    source "$PREFLIGHT"
    habitlab_xcode_preflight
    habitlab_xcode_run_external "$TEST_DIR/stubs/print-developer-dir"
)" || fail_test "supported pinned execution failed"
[[ "$pinned_output" == "$CANONICAL_DEVELOPER_ROOT" ]] ||
    fail_test "pinned execution did not use the canonical developer directory"

create_case too-old
if too_old_output="$(run_standalone 25.9 2>&1)"; then
    fail_test "too-old Xcode was accepted"
fi
[[ "$too_old_output" == *"or newer is required"* ]] || fail_test "too-old failure was not reported"

create_case malformed
if malformed_output="$(run_standalone malformed 2>&1)"; then
    fail_test "malformed Xcode version was accepted"
fi
[[ "$malformed_output" == *"could not parse"* ]] || fail_test "malformed version failure was not reported"

create_case missing-version
if missing_version_output="$(run_standalone '' 2>&1)"; then
    fail_test "missing Xcode version was accepted"
fi
[[ "$missing_version_output" == *"could not read"* ]] ||
    fail_test "missing version failure was not reported"

create_case missing-toolchain
if missing_output="$(env \
    DEVELOPER_DIR= \
    PATH="$STUB_DIR:$PATH" \
    STUB_DEVELOPER_DIR="$DEVELOPER_ROOT" \
    STUB_XCODEBUILD="$CASE_DIR/missing/xcodebuild" \
    STUB_XCODE_VERSION=26.4 \
    "$PREFLIGHT" 2>&1)"; then
    fail_test "missing xcodebuild was accepted"
fi
[[ "$missing_output" == *"unavailable"* ]] || fail_test "missing toolchain failure was not reported"

create_case identity-change
identity_log="$CASE_DIR/operations.log"
if identity_output="$(
    exec 2>&1
    export PATH="$STUB_DIR:$PATH"
    export DEVELOPER_DIR=
    export STUB_DEVELOPER_DIR="$DEVELOPER_ROOT"
    export STUB_XCODEBUILD="$XCODEBUILD_BIN"
    export STUB_XCODE_VERSION=26.4
    export STUB_OPERATION_LOG="$identity_log"
    source "$PREFLIGHT"
    habitlab_xcode_preflight
    replacement="$CASE_DIR/replacement-xcodebuild"
    cp "$TEST_DIR/stubs/xcodebuild" "$replacement"
    chmod +x "$replacement"
    mv "$replacement" "$XCODEBUILD_BIN"
    habitlab_xcode_run_external "$STUB_DIR/maestro" test
    )"; then
    fail_test "xcodebuild identity change after preflight was accepted"
fi
[[ "$identity_output" == *"identity changed after validation"* ]] ||
    fail_test "post-preflight identity mismatch was not reported"
[[ ! -s "$identity_log" ]] || fail_test "operation ran after xcodebuild identity mismatch"

create_case version-change
version_log="$CASE_DIR/operations.log"
if version_output="$(
    exec 2>&1
    export PATH="$STUB_DIR:$PATH"
    export DEVELOPER_DIR=
    export STUB_DEVELOPER_DIR="$DEVELOPER_ROOT"
    export STUB_XCODEBUILD="$XCODEBUILD_BIN"
    export STUB_XCODE_VERSION=26.4
    export STUB_OPERATION_LOG="$version_log"
    source "$PREFLIGHT"
    habitlab_xcode_preflight
    export STUB_XCODE_VERSION=26.5
    habitlab_xcode_run_external "$STUB_DIR/maestro" test
    )"; then
    fail_test "in-place xcodebuild version change was accepted"
fi
[[ "$version_output" == *"version changed after validation"* ]] ||
    fail_test "post-preflight version mismatch was not reported"
[[ ! -s "$version_log" ]] || fail_test "operation ran after xcodebuild version mismatch"

create_case global-selection-change
other_developer_root="$CASE_DIR/Other.app/Contents/Developer"
mkdir -p "$other_developer_root"
global_output="$(
    export PATH="$STUB_DIR:$PATH"
    export DEVELOPER_DIR=
    export STUB_DEVELOPER_DIR="$DEVELOPER_ROOT"
    export STUB_XCODEBUILD="$XCODEBUILD_BIN"
    export STUB_XCODE_VERSION=26.4
    source "$PREFLIGHT"
    habitlab_xcode_preflight
    export STUB_DEVELOPER_DIR="$other_developer_root"
    habitlab_xcode_run_external "$TEST_DIR/stubs/print-developer-dir"
)" || fail_test "global xcode-select change broke pinned execution"
[[ "$global_output" == "$CANONICAL_DEVELOPER_ROOT" ]] ||
    fail_test "global xcode-select change altered the pinned developer directory"

create_case best-effort
best_effort_log="$CASE_DIR/operations.log"
(
    export PATH="$STUB_DIR:$PATH"
    export DEVELOPER_DIR=
    export STUB_DEVELOPER_DIR="$DEVELOPER_ROOT"
    export STUB_XCODEBUILD="$XCODEBUILD_BIN"
    export STUB_XCODE_VERSION=26.4
    export STUB_TERMINATE_EXIT=97
    export STUB_OPERATION_LOG="$best_effort_log"
    source "$PREFLIGHT"
    habitlab_xcode_preflight
    habitlab_xcode_try_xcrun simctl terminate
) || fail_test "best-effort wrapper propagated its child failure"
[[ -s "$best_effort_log" ]] || fail_test "best-effort wrapper did not run its child"

create_case best-effort-validation
best_effort_validation_log="$CASE_DIR/operations.log"
if best_effort_validation_output="$(
    exec 2>&1
    export PATH="$STUB_DIR:$PATH"
    export DEVELOPER_DIR=
    export STUB_DEVELOPER_DIR="$DEVELOPER_ROOT"
    export STUB_XCODEBUILD="$XCODEBUILD_BIN"
    export STUB_XCODE_VERSION=26.4
    export STUB_OPERATION_LOG="$best_effort_validation_log"
    source "$PREFLIGHT"
    habitlab_xcode_preflight
    export STUB_XCODE_VERSION=26.5
    habitlab_xcode_try_xcrun simctl terminate
    )"; then
    fail_test "best-effort wrapper ignored validation failure"
fi
[[ "$best_effort_validation_output" == *"version changed after validation"* ]] ||
    fail_test "best-effort validation failure was not reported"
[[ ! -s "$best_effort_validation_log" ]] ||
    fail_test "best-effort child ran after validation failure"

create_case runner-validation-failure
runner_log="$CASE_DIR/operations.log"
version_count="$CASE_DIR/version-count"
if runner_output="$(
    unset DEVELOPER_DIR
    export PATH="$STUB_DIR:$PATH"
    export STUB_DEVELOPER_DIR="$DEVELOPER_ROOT"
    export STUB_XCODEBUILD="$XCODEBUILD_BIN"
    export STUB_XCODE_VERSION=26.4
    export STUB_XCODE_VERSION_AFTER=26.5
    export STUB_XCODE_VERSION_COUNT_FILE="$version_count"
    export STUB_OPERATION_LOG="$runner_log"
    "$RUNNER" ios 00000000-0000-0000-0000-000000000000 "$NEGATIVE_RUN_ID" 2>&1
)"; then
    fail_test "runner accepted a changed version before its first iOS operation"
fi
[[ "$runner_output" == *"version changed after validation"* ]] ||
    fail_test "runner did not report pre-operation validation failure"
[[ ! -s "$runner_log" ]] || fail_test "runner reached an iOS operation after validation failure"
delete_tree "$NEGATIVE_ARTIFACT_ROOT"
[[ ! -e "$NEGATIVE_ARTIFACT_ROOT" ]] || fail_test "negative runner artifacts were not cleaned"

create_case runner-happy-path
readonly HAPPY_DEVICE_ID=11111111-1111-1111-1111-111111111111
happy_log="$CASE_DIR/operations.log"
(
    unset DEVELOPER_DIR
    export PATH="$STUB_DIR:$PATH"
    export STUB_DEVELOPER_DIR="$DEVELOPER_ROOT"
    export STUB_XCODEBUILD="$XCODEBUILD_BIN"
    export STUB_XCODE_VERSION=26.4
    export STUB_DEVICE_ID="$HAPPY_DEVICE_ID"
    export STUB_TERMINATE_EXIT=91
    export STUB_OPERATION_LOG="$happy_log"
    "$RUNNER" ios "$HAPPY_DEVICE_ID" "$HAPPY_RUN_ID"
) || fail_test "stubbed iOS runner happy path failed"

readonly HAPPY_PLATFORM_ARTIFACTS="$HAPPY_ARTIFACT_ROOT/ios"
[[ -s "$HAPPY_PLATFORM_ARTIFACTS/command.log" ]] || fail_test "happy runner command log is missing"
grep -Fx 'xcode_version=26.4' "$HAPPY_PLATFORM_ARTIFACTS/command.log" >/dev/null ||
    fail_test "happy runner command log is missing the validated Xcode version"
[[ -s "$HAPPY_PLATFORM_ARTIFACTS/report.xml" ]] || fail_test "happy runner JUnit report is missing"
[[ -s "$HAPPY_PLATFORM_ARTIFACTS/debug/maestro.log" ]] || fail_test "happy runner debug log is missing"
[[ -s "$HAPPY_PLATFORM_ARTIFACTS/debug/commands-(reference-screens).json" ]] ||
    fail_test "happy runner command trace is missing"
happy_screenshot_count="$(find "$HAPPY_PLATFORM_ARTIFACTS/screenshots" -type f -name '*.png' | wc -l | tr -d '[:space:]')"
[[ "$happy_screenshot_count" == 3 ]] || fail_test "happy runner did not preserve three screenshots"

while IFS= read -r operation; do
    [[ "$operation" == "developer_dir=$CANONICAL_DEVELOPER_ROOT "* ]] ||
        fail_test "runner operation did not receive the canonical pinned developer directory"
done <"$happy_log"
for expected_operation in \
    "xcrun simctl list devices available" \
    "xcrun simctl bootstatus $HAPPY_DEVICE_ID -b" \
    "xcrun simctl getenv $HAPPY_DEVICE_ID SIMULATOR_MODEL_IDENTIFIER" \
    "xcodebuild -project iosApp/iosApp.xcodeproj" \
    "xcrun simctl terminate $HAPPY_DEVICE_ID com.denis.habitlab" \
    "xcrun simctl install $HAPPY_DEVICE_ID" \
    "maestro test --platform ios --udid $HAPPY_DEVICE_ID"; do
    grep -F "$expected_operation" "$happy_log" >/dev/null ||
        fail_test "happy runner did not execute: $expected_operation"
done

delete_tree "$HAPPY_ARTIFACT_ROOT"
[[ ! -e "$HAPPY_ARTIFACT_ROOT" ]] || fail_test "happy runner artifacts were not cleaned"

printf 'PASS: Xcode preflight and runner pinning checks\n'
