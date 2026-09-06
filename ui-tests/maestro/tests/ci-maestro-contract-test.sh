#!/usr/bin/env bash

set -Eeuo pipefail

readonly TEST_DIR="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly MAESTRO_DIR="$(cd -P -- "$TEST_DIR/.." && pwd -P)"
readonly REPOSITORY_ROOT="$(cd -P -- "$MAESTRO_DIR/../.." && pwd -P)"
readonly WORKFLOW="$REPOSITORY_ROOT/.github/workflows/shared-business-logic-tests.yml"
readonly MAESTRO_ARCHIVE_URL='https://github.com/mobile-dev-inc/maestro/releases/download/cli-2.6.1/maestro.zip'
readonly MAESTRO_ARCHIVE_SHA256='3440825f514f537c6a96bcf5de995780c2a4a7f83a43208fdc95d4f1fecfad3b'
readonly UPLOAD_ARTIFACT_ACTION_SHA='actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02'
readonly ANDROID_EMULATOR_ACTION_SHA='reactivecircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d'
readonly RUN_ID='ci-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}'
readonly WORKFLOW_ARTIFACT_RUN_ID='ci-${{ github.run_id }}-${{ github.run_attempt }}'

fail_test() {
    printf 'FAIL: %s\n' "$1" >&2
    exit 1
}

has_code_line() {
    local subject="$1"
    local expected="$2"

    printf '%s\n' "$subject" | EXPECTED_CODE_LINE="$expected" awk '
        BEGIN { expected = ENVIRON["EXPECTED_CODE_LINE"] }
        {
            line = $0
            sub(/^[[:space:]]+/, "", line)
            if (line ~ /^#/) {
                next
            }
            if (line == expected) {
                found = 1
            }
        }
        END { exit(found ? 0 : 1) }
    '
}

has_yaml_prefix() {
    local subject="$1"
    local expected="$2"

    printf '%s\n' "$subject" | EXPECTED_YAML_PREFIX="$expected" awk '
        BEGIN { expected = ENVIRON["EXPECTED_YAML_PREFIX"] }
        {
            line = $0
            sub(/^[[:space:]]+/, "", line)
            if (line ~ /^#/) {
                next
            }
            if (index(line, expected) == 1) {
                suffix = substr(line, length(expected) + 1)
                if (suffix == "" || suffix ~ /^[[:space:]]+#/) {
                    found = 1
                }
            }
        }
        END { exit(found ? 0 : 1) }
    '
}

has_code_fragment() {
    local subject="$1"
    local expected="$2"

    printf '%s\n' "$subject" | EXPECTED_CODE_FRAGMENT="$expected" awk '
        BEGIN { expected = ENVIRON["EXPECTED_CODE_FRAGMENT"] }
        {
            line = $0
            sub(/^[[:space:]]+/, "", line)
            if (line !~ /^#/ && index(line, expected) > 0) {
                found = 1
            }
        }
        END { exit(found ? 0 : 1) }
    '
}

code_line_count() {
    local subject="$1"
    local expected="$2"

    printf '%s\n' "$subject" | EXPECTED_CODE_LINE="$expected" awk '
        BEGIN { expected = ENVIRON["EXPECTED_CODE_LINE"] }
        {
            line = $0
            sub(/^[[:space:]]+/, "", line)
            if (line !~ /^#/ && line == expected) {
                count += 1
            }
        }
        END { print count + 0 }
    '
}

require_code_line() {
    local subject="$1"
    local expected="$2"
    local description="$3"

    has_code_line "$subject" "$expected" || fail_test "$description"
}

require_yaml_prefix() {
    local subject="$1"
    local expected="$2"
    local description="$3"

    has_yaml_prefix "$subject" "$expected" || fail_test "$description"
}

require_code_fragment() {
    local subject="$1"
    local expected="$2"
    local description="$3"

    has_code_fragment "$subject" "$expected" || fail_test "$description"
}

require_code_line_count() {
    local subject="$1"
    local expected="$2"
    local wanted_count="$3"
    local description="$4"
    local actual_count

    actual_count="$(code_line_count "$subject" "$expected")"
    [[ "$actual_count" == "$wanted_count" ]] ||
        fail_test "$description; expected $wanted_count occurrence(s), found $actual_count"
}

job_block() {
    local job_name="$1"

    awk -v header="  $job_name:" '
        $0 == header {
            if (found) {
                duplicate = 1
                exit
            }
            found = 1
            next
        }
        found && /^  [[:alnum:]_-]+:$/ { exit }
        found { print }
        END { exit(found && !duplicate ? 0 : 1) }
    ' "$WORKFLOW"
}

step_block() {
    local job="$1"
    local step_name="$2"

    printf '%s\n' "$job" | awk -v header="      - name: $step_name" '
        $0 == header {
            if (found) {
                duplicate = 1
                exit
            }
            found = 1
            print
            next
        }
        found && /^      - name: / { exit }
        found { print }
        END { exit(found && !duplicate ? 0 : 1) }
    '
}

job_preamble() {
    local job="$1"

    printf '%s\n' "$job" | awk '/^      - name: / { exit } { print }'
}

load_job() {
    local destination="$1"
    local job_name="$2"
    local extracted

    if ! extracted="$(job_block "$job_name")" || [[ -z "$extracted" ]]; then
        fail_test "workflow must contain exactly one $job_name job"
    fi
    printf -v "$destination" '%s' "$extracted"
}

load_step() {
    local destination="$1"
    local job="$2"
    local step_name="$3"
    local extracted

    if ! extracted="$(step_block "$job" "$step_name")" || [[ -z "$extracted" ]]; then
        fail_test "workflow must contain exactly one '$step_name' step in its expected job"
    fi
    printf -v "$destination" '%s' "$extracted"
}

assert_maestro_install_step() {
    local step="$1"
    local platform="$2"

    require_code_line "$step" 'shell: bash' "$platform Maestro install step must use Bash"
    require_code_line "$step" 'run: |' "$platform Maestro install step must be executable"
    require_code_line "$step" "curl --fail --location --silent --show-error --proto '=https' --tlsv1.2 \\" \
        "$platform Maestro install step must use the hardened downloader"
    require_code_line "$step" "$MAESTRO_ARCHIVE_URL" \
        "$platform Maestro install step must use the pinned archive URL"
    require_code_line "$step" "'$MAESTRO_ARCHIVE_SHA256' \\" \
        "$platform Maestro install step must carry the pinned SHA-256"
    require_code_line "$step" '"$maestro_archive" | shasum -a 256 -c -' \
        "$platform Maestro install step must verify the archive before extraction"
    require_code_line "$step" 'unzip -q "$maestro_archive" -d "$maestro_root"' \
        "$platform Maestro install step must extract the verified archive"
    require_code_line "$step" '[[ -x "$maestro_bin_dir/maestro" ]]' \
        "$platform Maestro install step must require an executable CLI"
    require_code_line "$step" 'printf '\''%s\n'\'' "$maestro_bin_dir" >> "$GITHUB_PATH"' \
        "$platform Maestro install step must publish the CLI path"
    require_code_line "$step" "[[ \"\$(maestro --version)\" == '2.6.1' ]]" \
        "$platform Maestro install step must verify the installed CLI version"
}

assert_evidence_verifier_step() {
    local step="$1"
    local platform="$2"
    local artifact_dir="build/maestro/$RUN_ID/$platform"

    require_code_line "$step" 'if: always()' "$platform evidence verifier must always run"
    require_code_line "$step" 'shell: bash' "$platform evidence verifier must use Bash"
    require_code_line "$step" "readonly artifact_dir=\"$artifact_dir\"" \
        "$platform evidence verifier must own its platform artifact directory"
    require_code_line "$step" 'require_non_empty "$artifact_dir/command.log"' \
        "$platform evidence verifier must require command.log"
    require_code_line "$step" 'require_non_empty "$artifact_dir/report.xml"' \
        "$platform evidence verifier must require report.xml"
    require_code_line "$step" 'require_non_empty "$artifact_dir/debug/maestro.log"' \
        "$platform evidence verifier must require the Maestro debug log"
    require_code_line "$step" 'require_non_empty "$artifact_dir/debug/commands-(reference-screens).json"' \
        "$platform evidence verifier must require the command trace"
    require_code_fragment "$step" '(( screenshot_count < 3 ))' \
        "$platform evidence verifier must require three screenshots"
}

assert_evidence_upload_step() {
    local step="$1"
    local platform="$2"
    local artifact_dir="build/maestro/$WORKFLOW_ARTIFACT_RUN_ID/$platform"

    require_code_line "$step" 'if: always()' "$platform evidence upload must always run"
    require_yaml_prefix "$step" "uses: $UPLOAD_ARTIFACT_ACTION_SHA" \
        "$platform evidence upload must use the pinned upload-artifact action"
    require_code_line "$step" 'with:' "$platform evidence upload must declare action inputs"
    require_code_line "$step" "name: maestro-$platform-$WORKFLOW_ARTIFACT_RUN_ID" \
        "$platform evidence upload must use a run-owned artifact name"
    require_code_line "$step" 'if-no-files-found: error' \
        "$platform evidence upload must fail when evidence is absent"
    require_code_line "$step" 'path: |' "$platform evidence upload must declare paths"
    require_code_line "$step" "$artifact_dir/command.log" \
        "$platform evidence upload must include command.log"
    require_code_line "$step" "$artifact_dir/report.xml" \
        "$platform evidence upload must include report.xml"
    require_code_line "$step" "$artifact_dir/screenshots/**" \
        "$platform evidence upload must include screenshots"
    require_code_line "$step" "$artifact_dir/debug/**" \
        "$platform evidence upload must include debug evidence"
    if [[ "$platform" == 'ios' ]]; then
        require_code_line "$step" "!$artifact_dir/DerivedData/**" \
            'iOS evidence upload must exclude DerivedData'
    fi
}

assert_step_extraction_negative_probe() {
    local fixture
    local selected_step
    local comment_only_fixture

    fixture=$'      - name: Install Maestro CLI 2.6.1\n        run: |\n          # https://github.com/mobile-dev-inc/maestro/releases/download/cli-2.6.1/maestro.zip\n          printf "%s\\n" ignored\n      - name: Unrelated helper\n        run: |\n          https://github.com/mobile-dev-inc/maestro/releases/download/cli-2.6.1/maestro.zip'
    if ! selected_step="$(step_block "$fixture" 'Install Maestro CLI 2.6.1')"; then
        fail_test 'step extractor did not find its exact named fixture step'
    fi
    if has_code_line "$selected_step" "$MAESTRO_ARCHIVE_URL"; then
        fail_test 'a comment or unrelated step satisfied the named-step contract probe'
    fi

    comment_only_fixture=$'      # - name: Install Maestro CLI 2.6.1\n        # https://github.com/mobile-dev-inc/maestro/releases/download/cli-2.6.1/maestro.zip'
    if step_block "$comment_only_fixture" 'Install Maestro CLI 2.6.1' >/dev/null; then
        fail_test 'a commented step header satisfied the named-step contract probe'
    fi
}

cleanup_inventory_probe() {
    if [[ -n "${INVENTORY_PROBE_ROOT:-}" && -d "$INVENTORY_PROBE_ROOT" ]]; then
        find "$INVENTORY_PROBE_ROOT" -depth -delete
    fi
}

cleanup_inventory_test_artifacts() {
    cleanup_inventory_probe
    if [[ -n "${INVENTORY_CREATE_SCRIPT:-}" && -f "$INVENTORY_CREATE_SCRIPT" ]]; then
        find "$INVENTORY_CREATE_SCRIPT" -depth -delete
    fi
}

extract_create_step_run_script() {
    local step="$1"
    local destination="$2"

    if ! printf '%s\n' "$step" | awk '
        $0 == "        run: |" {
            found = 1
            next
        }
        found {
            if (substr($0, 1, 10) != "          ") {
                malformed = 1
                exit
            }
            print substr($0, 11)
        }
        END { exit(found && !malformed ? 0 : 1) }
    ' >"$destination"; then
        fail_test 'could not extract the executable iOS simulator creation step'
    fi
    chmod +x "$destination"
}

write_inventory_probe_stubs() {
    local stub_dir="$1"

    mkdir -p "$stub_dir"
    printf '%s\n' \
        '#!/usr/bin/env bash' \
        'set -Eeuo pipefail' \
        'printf "xcrun %s\\n" "$*" >> "${STUB_XCRUN_LOG:?}"' \
        'case "$*" in' \
        '    "simctl list runtimes -j") printf "{\\\"runtimes\\\":[]}\\n" ;;' \
        '    "simctl list devicetypes -j") printf "{\\\"devicetypes\\\":[]}\\n" ;;' \
        '    "simctl list devices -j") printf "{\\\"devices\\\":{}}\\n" ;;' \
        '    "simctl create "*) printf "00000000-0000-0000-0000-000000000000\\n" ;;' \
        '    *) printf "unexpected xcrun invocation: %s\\n" "$*" >&2; exit 97 ;;' \
        'esac' >"$stub_dir/xcrun"
    printf '%s\n' \
        '#!/usr/bin/env bash' \
        'set -Eeuo pipefail' \
        'case "$*" in' \
        '    *".runtimes[]"*)' \
        '        if [[ "$*" == -c* ]]; then printf "[]\\n"; else printf "%s\\n" "${STUB_RUNTIME_MATCH_COUNT:?}"; fi' \
        '        ;;' \
        '    *".devicetypes[]"*)' \
        '        if [[ "$*" == -c* ]]; then printf "[]\\n"; else printf "%s\\n" "${STUB_DEVICE_TYPE_MATCH_COUNT:?}"; fi' \
        '        ;;' \
        '    *".devices[]"*) exit 0 ;;' \
        '    *) printf "unexpected jq invocation: %s\\n" "$*" >&2; exit 97 ;;' \
        'esac' >"$stub_dir/jq"
    chmod +x "$stub_dir/xcrun" "$stub_dir/jq"
}

write_mktemp_failure_stub() {
    local stub_dir="$1"

    printf '%s\n' \
        '#!/usr/bin/env bash' \
        'set -Eeuo pipefail' \
        'printf "mktemp failure\\n" >&2' \
        'exit 73' >"$stub_dir/mktemp"
    chmod +x "$stub_dir/mktemp"
}

assert_inventory_failure_is_diagnostic() {
    local create_script="$1"
    local inventory_kind="$2"
    local runtime_match_count=1
    local device_type_match_count=1
    local expected_identifier=''
    local output=''
    local xcrun_log=''
    local stub_dir=''

    case "$inventory_kind" in
        runtime)
            runtime_match_count=0
            expected_identifier='com.apple.CoreSimulator.SimRuntime.iOS-26-4'
            ;;
        device-type)
            device_type_match_count=0
            expected_identifier='com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro'
            ;;
        *) fail_test "unknown inventory probe kind: $inventory_kind" ;;
    esac

    INVENTORY_PROBE_ROOT="$(mktemp -d)"
    stub_dir="$INVENTORY_PROBE_ROOT/stubs"
    xcrun_log="$INVENTORY_PROBE_ROOT/xcrun.log"
    mkdir -p "$INVENTORY_PROBE_ROOT/runner-temp"
    write_inventory_probe_stubs "$stub_dir"

    if output="$(env \
        RUNNER_TEMP="$INVENTORY_PROBE_ROOT/runner-temp" \
        GITHUB_RUN_ID='inventory-probe' \
        GITHUB_RUN_ATTEMPT='1' \
        GITHUB_OUTPUT="$INVENTORY_PROBE_ROOT/github-output" \
        STUB_RUNTIME_MATCH_COUNT="$runtime_match_count" \
        STUB_DEVICE_TYPE_MATCH_COUNT="$device_type_match_count" \
        STUB_XCRUN_LOG="$xcrun_log" \
        PATH="$stub_dir:$PATH" \
        "$BASH" "$create_script" 2>&1)"; then
        cleanup_inventory_probe
        INVENTORY_PROBE_ROOT=''
        fail_test "$inventory_kind inventory mismatch unexpectedly reached a successful create step"
    fi
    if ! printf '%s\n' "$output" | grep -Fq -- "$expected_identifier"; then
        cleanup_inventory_probe
        INVENTORY_PROBE_ROOT=''
        fail_test "$inventory_kind inventory mismatch did not identify $expected_identifier"
    fi
    if ! printf '%s\n' "$output" | grep -Eiq 'error|unavailable|missing|not found|expected'; then
        cleanup_inventory_probe
        INVENTORY_PROBE_ROOT=''
        fail_test "$inventory_kind inventory mismatch did not provide an actionable diagnostic"
    fi
    if [[ -f "$xcrun_log" ]] && grep -Fq 'simctl create' "$xcrun_log"; then
        cleanup_inventory_probe
        INVENTORY_PROBE_ROOT=''
        fail_test "$inventory_kind inventory mismatch reached simctl create"
    fi

    cleanup_inventory_probe
    INVENTORY_PROBE_ROOT=''
}

assert_mktemp_failure_stops_before_simctl_create() {
    local create_script="$1"
    local output=''
    local xcrun_log=''
    local stub_dir=''

    INVENTORY_PROBE_ROOT="$(mktemp -d)"
    stub_dir="$INVENTORY_PROBE_ROOT/stubs"
    xcrun_log="$INVENTORY_PROBE_ROOT/xcrun.log"
    mkdir -p "$INVENTORY_PROBE_ROOT/runner-temp"
    write_inventory_probe_stubs "$stub_dir"
    write_mktemp_failure_stub "$stub_dir"

    if output="$(env \
        RUNNER_TEMP="$INVENTORY_PROBE_ROOT/runner-temp" \
        GITHUB_RUN_ID='mktemp-probe' \
        GITHUB_RUN_ATTEMPT='1' \
        GITHUB_OUTPUT="$INVENTORY_PROBE_ROOT/github-output" \
        STUB_RUNTIME_MATCH_COUNT=1 \
        STUB_DEVICE_TYPE_MATCH_COUNT=1 \
        STUB_XCRUN_LOG="$xcrun_log" \
        PATH="$stub_dir:$PATH" \
        "$BASH" "$create_script" 2>&1)"; then
        cleanup_inventory_probe
        INVENTORY_PROBE_ROOT=''
        fail_test 'mktemp failure unexpectedly reached a successful create step'
    fi
    if ! printf '%s\n' "$output" | grep -Eiq 'error.*(mktemp|temporary|output)|could not.*(mktemp|temporary|output)'; then
        cleanup_inventory_probe
        INVENTORY_PROBE_ROOT=''
        fail_test 'mktemp failure did not produce a direct create-output diagnostic'
    fi
    if [[ -f "$xcrun_log" ]] && grep -Fq 'simctl create' "$xcrun_log"; then
        cleanup_inventory_probe
        INVENTORY_PROBE_ROOT=''
        fail_test 'mktemp failure reached simctl create'
    fi

    cleanup_inventory_probe
    INVENTORY_PROBE_ROOT=''
}

[[ -f "$WORKFLOW" ]] || fail_test 'shared CI workflow is missing'
assert_step_extraction_negative_probe
INVENTORY_PROBE_ROOT=''
INVENTORY_CREATE_SCRIPT=''
trap cleanup_inventory_test_artifacts EXIT

load_job ios_job ios-simulator
load_job android_job android-emulator
ios_preamble="$(job_preamble "$ios_job")"
require_code_line "$ios_preamble" 'DEVELOPER_DIR: /Applications/Xcode_26.4.1.app/Contents/Developer' \
    'iOS job must select Xcode 26.4.1 in its own environment'

load_step ios_xcode "$ios_job" 'Verify selected Xcode'
require_code_line "$ios_xcode" 'shell: bash' 'Xcode verification step must use Bash'
require_code_line "$ios_xcode" 'xcode_version_output="$(xcodebuild -version)"' \
    'Xcode verification step must inspect the selected Xcode'
require_code_line "$ios_xcode" "[[ \"\$(printf '%s\\n' \"\$xcode_version_output\" | sed -n '1p')\" == 'Xcode 26.4.1' ]]" \
    'Xcode verification step must require exactly Xcode 26.4.1'

load_step ios_maestro_install "$ios_job" 'Install Maestro CLI 2.6.1'
load_step android_maestro_install "$android_job" 'Install Maestro CLI 2.6.1'
assert_maestro_install_step "$ios_maestro_install" 'iOS'
assert_maestro_install_step "$android_maestro_install" 'Android'

load_step ios_create "$ios_job" 'Create job-owned iOS 26.4 simulator'
require_code_line "$ios_create" 'id: create-ios-simulator' \
    'iOS simulator creation step must expose its output ID'
require_code_line "$ios_create" 'shell: bash' 'iOS simulator creation step must use Bash'
require_code_line "$ios_create" "readonly runtime_identifier='com.apple.CoreSimulator.SimRuntime.iOS-26-4'" \
    'iOS simulator creation step must pin the iOS 26.4 runtime'
require_code_line "$ios_create" "readonly device_type_identifier='com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro'" \
    'iOS simulator creation step must pin the iPhone 17 Pro device type'
require_code_fragment "$ios_create" 'mktemp "$RUNNER_TEMP/habit-lab-den21-simctl-create-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}.XXXXXX"' \
    'iOS simulator creation step must create a run-owned simctl snapshot file'
require_code_line "$ios_create" 'candidate="$(tr -d '\''\r\n'\'' < "$create_output_file")"' \
    'iOS simulator creation step must read the created UDID from its snapshot'
require_code_line "$ios_create" 'xcrun simctl create "$simulator_name" "$device_type_identifier" "$runtime_identifier" > "$create_output_file"' \
    'iOS simulator creation step must create the pinned simulator into its snapshot'
require_code_line "$ios_create" 'created_udid="$(read_created_udid)"' \
    'iOS simulator creation step must validate the created UDID'
for trap_spec in \
    'trap cleanup_on_exit EXIT' \
    'trap cleanup_on_error ERR' \
    'trap cleanup_on_hup HUP' \
    'trap cleanup_on_int INT' \
    'trap cleanup_on_term TERM'; do
    require_code_line "$ios_create" "$trap_spec" \
        "iOS simulator creation step must install $trap_spec"
done
require_code_line_count "$ios_create" 'trap - EXIT ERR HUP INT TERM' 2 \
    'iOS simulator creation step must disarm traps after cleanup and ownership transfer'
require_code_line "$ios_create" 'xcrun simctl shutdown "$cleanup_udid" >/dev/null 2>&1 || true' \
    'iOS simulator creation trap must best-effort shut down only its candidate'
require_code_line "$ios_create" 'xcrun simctl delete "$cleanup_udid" >/dev/null 2>&1 || true' \
    'iOS simulator creation trap must delete only its candidate'
require_code_line "$ios_create" "printf 'name=%s\\nudid=%s\\n' \"\$simulator_name\" \"\$created_udid\" >> \"\$GITHUB_OUTPUT\"" \
    'iOS simulator creation step must publish its owned simulator outputs'
INVENTORY_CREATE_SCRIPT="$(mktemp)"
extract_create_step_run_script "$ios_create" "$INVENTORY_CREATE_SCRIPT"
assert_inventory_failure_is_diagnostic "$INVENTORY_CREATE_SCRIPT" runtime
assert_inventory_failure_is_diagnostic "$INVENTORY_CREATE_SCRIPT" device-type
assert_mktemp_failure_stops_before_simctl_create "$INVENTORY_CREATE_SCRIPT"
cleanup_inventory_test_artifacts
INVENTORY_CREATE_SCRIPT=''

load_step ios_runner "$ios_job" 'Run Maestro parity smoke on the job-owned iOS simulator'
require_code_line "$ios_runner" 'shell: bash' 'iOS Maestro runner step must use Bash'
require_code_line "$ios_runner" 'MAESTRO_IOS_UDID: ${{ steps.create-ios-simulator.outputs.udid }}' \
    'iOS Maestro runner step must consume the creation UDID output'
require_code_line "$ios_runner" "run: ./ui-tests/maestro/run.sh ios \"\$MAESTRO_IOS_UDID\" $RUN_ID" \
    'iOS Maestro runner step must invoke the shared runner with that UDID'

load_step android_runner "$android_job" 'Run common tests on one Android emulator'
require_yaml_prefix "$android_runner" "uses: $ANDROID_EMULATOR_ACTION_SHA" \
    'Android runner step must use the pinned emulator action'
require_code_line "$android_runner" 'with:' 'Android runner step must declare action inputs'
require_code_line "$android_runner" 'api-level: 36' 'Android runner step must pin API 36'
require_code_line "$android_runner" 'target: google_apis' 'Android runner step must pin Google APIs'
require_code_line "$android_runner" 'arch: x86_64' 'Android runner step must pin x86_64'
require_code_line "$android_runner" 'script: |' 'Android runner step must execute its runner script'
require_code_line "$android_runner" "./ui-tests/maestro/run.sh android emulator-5554 $RUN_ID" \
    'Android runner step must invoke the shared runner on its emulator'

load_step ios_verifier "$ios_job" 'Verify iOS Maestro canonical evidence'
load_step android_verifier "$android_job" 'Verify Android Maestro canonical evidence'
assert_evidence_verifier_step "$ios_verifier" 'ios'
assert_evidence_verifier_step "$android_verifier" 'android'

load_step ios_upload "$ios_job" 'Upload iOS Maestro evidence'
load_step android_upload "$android_job" 'Upload Android Maestro evidence'
assert_evidence_upload_step "$ios_upload" 'ios'
assert_evidence_upload_step "$android_upload" 'android'

load_step ios_cleanup "$ios_job" 'Clean up job-owned iOS simulator'
require_code_line "$ios_cleanup" 'if: always()' 'iOS simulator cleanup step must always run'
require_code_line "$ios_cleanup" 'shell: bash' 'iOS simulator cleanup step must use Bash'
require_code_line "$ios_cleanup" 'CREATED_IOS_SIMULATOR_NAME: ${{ steps.create-ios-simulator.outputs.name }}' \
    'iOS simulator cleanup step must consume the creation name output'
require_code_line "$ios_cleanup" 'CREATED_IOS_SIMULATOR_UDID: ${{ steps.create-ios-simulator.outputs.udid }}' \
    'iOS simulator cleanup step must consume the creation UDID output'
require_code_line "$ios_cleanup" 'xcrun simctl list devices | grep -F "$CREATED_IOS_SIMULATOR_NAME ($CREATED_IOS_SIMULATOR_UDID)" >/dev/null' \
    'iOS simulator cleanup step must prove simulator ownership by name and UDID'
require_code_line "$ios_cleanup" 'xcrun simctl shutdown "$CREATED_IOS_SIMULATOR_UDID" || true' \
    'iOS simulator cleanup step must best-effort shut down only its owned simulator'
require_code_line "$ios_cleanup" 'xcrun simctl delete "$CREATED_IOS_SIMULATOR_UDID"' \
    'iOS simulator cleanup step must delete only its owned simulator'

printf 'Maestro CI contract passed: named executable steps, pinned tools, evidence, and iOS ownership\n'
