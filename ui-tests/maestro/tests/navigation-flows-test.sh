#!/usr/bin/env bash

set -Eeuo pipefail

TEST_DIR="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)" || exit 1
readonly TEST_DIR
MAESTRO_DIR="$(cd -P -- "$TEST_DIR/.." && pwd -P)" || exit 1
readonly MAESTRO_DIR
REPOSITORY_ROOT="$(cd -P -- "$MAESTRO_DIR/../.." && pwd -P)" || exit 1
readonly REPOSITORY_ROOT
readonly RUNNER_FLOW_DIR="$MAESTRO_DIR/flows"
readonly ROOT_FLOW="$RUNNER_FLOW_DIR/reference-screens.yaml"
readonly PLATFORM_FLOW_DIR="$RUNNER_FLOW_DIR/platform"
readonly SKILL_FLOW_DIR="$REPOSITORY_ROOT/.agents/skills/habit-lab-autodev/flows"
readonly IOS_EDGE_FLOW="$PLATFORM_FLOW_DIR/ios-edge-back.yaml"
readonly RESOLVED_ROOT_DEPENDENCIES="$(mktemp)"

cleanup() {
    rm -f -- "$RESOLVED_ROOT_DEPENDENCIES"
}
trap cleanup EXIT

fail_test() {
    printf 'FAIL: %s\n' "$1" >&2
    exit 1
}

canonical_file() {
    local path="$1"
    local directory
    local filename

    [[ -f "$path" ]] || fail_test "referenced flow does not exist: $path"
    directory="$(cd -P -- "$(dirname -- "$path")" && pwd -P)"
    filename="$(basename -- "$path")"
    printf '%s/%s\n' "$directory" "$filename"
}

assert_flow_shape() {
    local flow="$1"
    local first_line
    local separator_count
    local meaningful_tail
    local final_command
    local final_selector

    first_line="$(sed -n '1p' "$flow")"
    [[ "$first_line" == "appId: com.denis.habitlab" ]] ||
        fail_test "$flow does not declare the Habit Lab appId first"

    separator_count="$(grep -Ec '^---[[:space:]]*$' "$flow" || true)"
    [[ "$separator_count" == 1 ]] || fail_test "$flow must contain exactly one YAML document separator"
    ! grep -q $'\t' "$flow" || fail_test "$flow contains a tab and is not portable YAML"
    grep -Eq '^-[[:space:]]+[[:alnum:]][[:alnum:]-]*(:|$)' "$flow" ||
        fail_test "$flow does not contain a command list"

    meaningful_tail="$(sed -E '/^[[:space:]]*($|#)/d; s/[[:space:]]+$//' "$flow" | tail -n 2)"
    final_command="$(printf '%s\n' "$meaningful_tail" | sed -n '1p')"
    final_selector="$(printf '%s\n' "$meaningful_tail" | sed -n '2p')"
    [[ "$final_command" == "- assertVisible:" ]] ||
        fail_test "$flow must end with an assertVisible command"
    [[ "$final_selector" =~ ^[[:space:]]+id:[[:space:]]habitlab\.[a-z0-9-]+\.screen\.root$ ]] ||
        fail_test "$flow must end by asserting a semantic screen root"
}

assert_resolved_run_flows() {
    local source_flow="$1"
    local referenced_path
    local resolved

    while IFS= read -r referenced_path; do
        [[ "$referenced_path" != /* ]] || fail_test "$source_flow contains an absolute runFlow path"
        resolved="$(canonical_file "$(dirname -- "$source_flow")/$referenced_path")"
        [[ "$resolved" == "$REPOSITORY_ROOT/"* ]] ||
            fail_test "$source_flow resolves a runFlow outside the repository"
        if [[ "$source_flow" == "$ROOT_FLOW" ]]; then
            case "$resolved" in
                "$SKILL_FLOW_DIR/"*|"$PLATFORM_FLOW_DIR/"*) ;;
                *) fail_test "$source_flow delegates to an unowned flow: $resolved" ;;
            esac
            printf '%s\n' "$resolved" >>"$RESOLVED_ROOT_DEPENDENCIES"
        fi
    done < <(sed -nE 's/^[[:space:]]+file:[[:space:]]+([^#[:space:]]+)[[:space:]]*$/\1/p' "$source_flow")
}

assert_no_app_selector_fallbacks() {
    local flow="$1"

    ! grep -Eq '^[[:space:]]+(text|index|point):' "$flow" ||
        fail_test "$flow uses a text, index, or point selector"
    ! grep -Eq '^[[:space:]]*-[[:space:]]*(tapOn|assertVisible|assertNotVisible|longPressOn):[[:space:]]+[^[:space:]]' "$flow" ||
        fail_test "$flow uses a shorthand non-ID app selector"
    ! grep -Eq '^[[:space:]]+visible:[[:space:]]+[^[:space:]]' "$flow" ||
        fail_test "$flow uses a shorthand visible selector"
    ! grep -Eq '[0-9]+%?[[:space:]]*,[[:space:]]*[0-9]+%?' "$flow" ||
        fail_test "$flow uses coordinates for an app-owned interaction"
    ! grep -Eq '^[[:space:]]*-[[:space:]]+swipe:' "$flow" ||
        fail_test "$flow contains a system gesture outside the platform flow directory"
}

top_level_count="$(find "$RUNNER_FLOW_DIR" -maxdepth 1 -type f \( -name '*.yaml' -o -name '*.yml' \) | wc -l | tr -d '[:space:]')"
[[ "$top_level_count" == 1 ]] || fail_test "reference-screens.yaml must remain the sole runner-owned top-level flow"
[[ -f "$ROOT_FLOW" ]] || fail_test "the runner-owned reference-screens.yaml flow is missing"

assert_flow_shape "$ROOT_FLOW"
assert_resolved_run_flows "$ROOT_FLOW"
assert_no_app_selector_fallbacks "$ROOT_FLOW"
root_run_flow_count="$(grep -Ec '^-[[:space:]]+runFlow:' "$ROOT_FLOW" || true)"
root_file_count="$(sed -nE 's/^[[:space:]]+file:[[:space:]]+([^#[:space:]]+)[[:space:]]*$/\1/p' "$ROOT_FLOW" | wc -l | tr -d '[:space:]')"
[[ "$root_run_flow_count" == "$root_file_count" ]] ||
    fail_test "every root runFlow command must resolve through an explicit file"

skill_flow_count=0
while IFS= read -r skill_flow; do
    skill_flow_count=$((skill_flow_count + 1))
    assert_flow_shape "$skill_flow"
    assert_resolved_run_flows "$skill_flow"
    assert_no_app_selector_fallbacks "$skill_flow"
    grep -Fqx "$(canonical_file "$skill_flow")" "$RESOLVED_ROOT_DEPENDENCIES" ||
        fail_test "$skill_flow is not composed by the runner-owned root flow"
done < <(find "$SKILL_FLOW_DIR" -maxdepth 1 -type f \( -name '*.yaml' -o -name '*.yml' \) | sort)
(( skill_flow_count > 0 )) || fail_test "no reviewed skill-owned navigation subflows were found"

while IFS= read -r platform_flow; do
    assert_flow_shape "$platform_flow"
    assert_resolved_run_flows "$platform_flow"
    grep -Fqx "$(canonical_file "$platform_flow")" "$RESOLVED_ROOT_DEPENDENCIES" ||
        fail_test "$platform_flow is not composed by the runner-owned root flow"
done < <(find "$PLATFORM_FLOW_DIR" -maxdepth 1 -type f \( -name '*.yaml' -o -name '*.yml' \) | sort)

while IFS= read -r coordinate_flow; do
    [[ "$coordinate_flow" == "$IOS_EDGE_FLOW" ]] ||
        fail_test "coordinates are only allowed in the iOS system edge-back flow: $coordinate_flow"
done < <(grep -El '[0-9]+%?[[:space:]]*,[[:space:]]*[0-9]+%?' "$ROOT_FLOW" "$SKILL_FLOW_DIR"/*.yaml "$PLATFORM_FLOW_DIR"/*.yaml || true)

[[ "$(grep -Ec '^[[:space:]]+(start|end):[[:space:]][0-9]+%,[[:space:]][0-9]+%$' "$IOS_EDGE_FLOW" || true)" == 4 ]] ||
    fail_test "the iOS edge-back exception must remain four percentage-coordinate endpoints"
! grep -Eq '^[[:space:]]+(text|index|point):' "$IOS_EDGE_FLOW" ||
    fail_test "the iOS system gesture flow contains an app selector fallback"

printf 'Maestro navigation flow contract passed: %s reviewed subflows\n' "$skill_flow_count"
