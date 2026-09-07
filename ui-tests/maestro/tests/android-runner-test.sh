#!/usr/bin/env bash

set -Eeuo pipefail

readonly TEST_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly MAESTRO_DIR="$(cd -- "$TEST_DIR/.." && pwd)"
readonly TEST_ROOT="$(mktemp -d)"

delete_tree() {
    local path="$1"

    if [[ -d "$path" ]]; then
        find "$path" -depth -delete
    fi
}

cleanup() {
    delete_tree "$TEST_ROOT"
}
trap cleanup EXIT

fail_test() {
    printf 'FAIL: %s\n' "$1" >&2
    exit 1
}

count_lines() {
    local needle="$1"
    local path="$2"

    grep -cF -- "$needle" "$path" || true
}

create_case() {
    local name="$1"

    CASE_ROOT="$TEST_ROOT/$name"
    CASE_REPOSITORY="$CASE_ROOT/repository"
    CASE_STUBS="$CASE_ROOT/stubs"
    CASE_JAVA_HOME="$CASE_ROOT/jdk"
    CASE_OPERATIONS="$CASE_ROOT/operations.log"
    CASE_STATES="$CASE_ROOT/adb-states"
    mkdir -p "$CASE_REPOSITORY/ui-tests/maestro/flows" "$CASE_STUBS" "$CASE_JAVA_HOME/bin"

    cp "$MAESTRO_DIR/run.sh" "$CASE_REPOSITORY/ui-tests/maestro/run.sh"
    cp "$MAESTRO_DIR/config.yaml" "$CASE_REPOSITORY/ui-tests/maestro/config.yaml"
    cp "$MAESTRO_DIR/flows/reference-screens.yaml" "$CASE_REPOSITORY/ui-tests/maestro/flows/reference-screens.yaml"
    cp "$TEST_DIR/stubs/adb" "$TEST_DIR/stubs/gradlew" "$TEST_DIR/stubs/maestro" \
        "$TEST_DIR/stubs/sleep" "$CASE_STUBS/"
    cp "$TEST_DIR/stubs/java" "$CASE_JAVA_HOME/bin/java"
    cp "$TEST_DIR/stubs/gradlew" "$CASE_REPOSITORY/gradlew"
    chmod +x "$CASE_REPOSITORY/ui-tests/maestro/run.sh" "$CASE_STUBS"/* \
        "$CASE_JAVA_HOME/bin/java" "$CASE_REPOSITORY/gradlew"
}

run_android() {
    local run_id="$1"
    shift

    (
        cd "$CASE_ROOT"
        env \
            JAVA_HOME="$CASE_JAVA_HOME" \
            PATH="$CASE_STUBS:$PATH" \
            STUB_OPERATION_LOG="$CASE_OPERATIONS" \
            STUB_ADB_STATES_FILE="$CASE_STATES" \
            "$@" \
            "$CASE_REPOSITORY/ui-tests/maestro/run.sh" android emulator-5554 "$run_id"
    )
}

create_case transport-recovery
printf 'device\noffline\noffline\ndevice\n' >"$CASE_STATES"
run_android recovery-success DEVELOPER_DIR=/Applications/Xcode_26.4.1.app/Contents/Developer
readonly RECOVERY_ARTIFACTS="$CASE_REPOSITORY/build/maestro/recovery-success/android"
[[ -s "$RECOVERY_ARTIFACTS/report.xml" ]] || fail_test 'recovered Android runner did not produce JUnit evidence'
[[ -s "$RECOVERY_ARTIFACTS/debug/maestro.log" ]] || fail_test 'recovered Android runner did not produce debug evidence'
[[ "$(count_lines 'adb -s emulator-5554 get-state' "$CASE_OPERATIONS")" == 4 ]] ||
    fail_test 'runner did not retry Android transport before Maestro'
[[ "$(count_lines 'sleep 2' "$CASE_OPERATIONS")" == 2 ]] ||
    fail_test 'runner did not use bounded transport backoff'
grep -F 'adb -s emulator-5554 shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER com.denis.habitlab' \
    "$CASE_OPERATIONS" >/dev/null || fail_test 'runner did not resolve the installed launcher activity'
grep -F 'maestro test --platform android --udid emulator-5554' "$CASE_OPERATIONS" >/dev/null ||
    fail_test 'runner did not invoke Maestro after Android transport recovery'

create_case permanent-offline
printf 'device\noffline\n' >"$CASE_STATES"
if permanent_offline_output="$(run_android permanent-offline STUB_ADB_DEFAULT_STATE=offline 2>&1)"; then
    fail_test 'runner accepted a permanently offline Android transport'
fi
[[ "$permanent_offline_output" == *"did not expose a launcher target after install"* ]] ||
    fail_test 'permanent offline transport did not produce a launcher-readiness failure'
[[ "$(count_lines 'maestro test' "$CASE_OPERATIONS")" == 0 ]] ||
    fail_test 'runner invoked Maestro when Android transport could not recover'

create_case missing-launch-target
printf 'device\n' >"$CASE_STATES"
if missing_target_output="$(run_android missing-target STUB_ADB_LAUNCH_TARGET=other.package/.Activity 2>&1)"; then
    fail_test 'runner accepted an Android package without its launcher target'
fi
[[ "$missing_target_output" == *"did not expose a launcher target after install"* ]] ||
    fail_test 'missing Android launcher target did not fail closed'
[[ "$(count_lines 'maestro test' "$CASE_OPERATIONS")" == 0 ]] ||
    fail_test 'runner invoked Maestro without a resolved Android launcher target'

create_case maestro-failure
printf 'device\n' >"$CASE_STATES"
if maestro_failure_output="$(run_android maestro-failure STUB_MAESTRO_EXIT=73 2>&1)"; then
    fail_test 'runner accepted a failing Maestro flow'
fi
[[ "$(count_lines 'maestro test --platform android --udid emulator-5554' "$CASE_OPERATIONS")" == 1 ]] ||
    fail_test 'runner retried a failing Maestro flow instead of preserving the app failure'
[[ "$maestro_failure_output" != *'waiting for Android package'* ]] ||
    fail_test 'runner retried transport after Maestro had started'

printf 'PASS: Android runner transport recovery and fail-closed launch readiness checks\n'
