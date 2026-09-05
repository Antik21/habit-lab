#!/usr/bin/env bash

if [[ "${HABITLAB_XCODE_PREFLIGHT_DIR+x}" == x || \
    "${HABITLAB_XCODE_VERSION_LIBRARY+x}" == x || \
    "${HABITLAB_MIN_XCODE_MAJOR+x}" == x || \
    "${HABITLAB_MIN_XCODE_MINOR+x}" == x || \
    "${HABITLAB_SYSTEM_XCRUN+x}" == x || \
    "${HABITLAB_SYSTEM_XCODE_SELECT+x}" == x || \
    "${HABITLAB_XCODE_DEVELOPER_DIR+x}" == x || \
    "${HABITLAB_XCODE_DEVELOPER_IDENTITY+x}" == x || \
    "${HABITLAB_XCRUN_BIN+x}" == x || \
    "${HABITLAB_XCRUN_IDENTITY+x}" == x || \
    "${HABITLAB_XCODEBUILD_BIN+x}" == x || \
    "${HABITLAB_XCODEBUILD_IDENTITY+x}" == x || \
    "${HABITLAB_XCODE_VERSION+x}" == x ]] || \
    declare -F habitlab_parse_xcode_version >/dev/null || \
    declare -F habitlab_xcode_version_is_at_least >/dev/null || \
    declare -F habitlab_xcode_error >/dev/null || \
    declare -F habitlab_canonicalize_directory >/dev/null || \
    declare -F habitlab_canonicalize_file >/dev/null || \
    declare -F habitlab_file_identity >/dev/null || \
    declare -F habitlab_require_single_line >/dev/null || \
    declare -F habitlab_resolve_selector >/dev/null || \
    declare -F habitlab_resolve_xcrun >/dev/null || \
    declare -F habitlab_resolve_xcode_select >/dev/null || \
    declare -F habitlab_resolve_developer_dir >/dev/null || \
    declare -F habitlab_resolve_xcodebuild >/dev/null || \
    declare -F habitlab_read_xcode_version >/dev/null || \
    declare -F habitlab_xcode_preflight >/dev/null || \
    declare -F habitlab_xcode_verify_pinned >/dev/null || \
    declare -F habitlab_xcode_run_xcodebuild >/dev/null || \
    declare -F habitlab_xcode_run_xcrun >/dev/null || \
    declare -F habitlab_xcode_try_xcrun >/dev/null || \
    declare -F habitlab_xcode_run_external >/dev/null; then
    printf 'error: Habit Lab Xcode preflight symbol collision\n' >&2
    if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
        exit 2
    fi
    return 2
fi

readonly HABITLAB_XCODE_PREFLIGHT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly HABITLAB_XCODE_VERSION_LIBRARY="$HABITLAB_XCODE_PREFLIGHT_DIR/lib/xcode-version.sh"
readonly HABITLAB_MIN_XCODE_MAJOR=26
readonly HABITLAB_MIN_XCODE_MINOR=4
readonly HABITLAB_SYSTEM_XCRUN=/usr/bin/xcrun
readonly HABITLAB_SYSTEM_XCODE_SELECT=/usr/bin/xcode-select

# shellcheck source=lib/xcode-version.sh
if ! source "$HABITLAB_XCODE_VERSION_LIBRARY"; then
    printf 'error: could not load the Habit Lab Xcode version helper\n' >&2
    if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
        exit 2
    fi
    return 2
fi

habitlab_xcode_error() {
    printf 'error: %s\n' "$1" >&2
    return 2
}

habitlab_canonicalize_directory() {
    (cd -P -- "$1" 2>/dev/null && pwd -P)
}

habitlab_canonicalize_file() {
    local parent
    local filename

    parent="$(habitlab_canonicalize_directory "$(dirname -- "$1")")" || return 1
    filename="$(basename -- "$1")"
    printf '%s/%s\n' "$parent" "$filename"
}

habitlab_file_identity() {
    if /usr/bin/stat -c '%d:%i' "$1" >/dev/null 2>&1; then
        /usr/bin/stat -c '%d:%i' "$1"
    else
        /usr/bin/stat -f '%d:%i' "$1" 2>/dev/null
    fi
}

habitlab_require_single_line() {
    [[ -n "$1" && "$1" != *$'\n'* && "$1" != *$'\r'* ]] ||
        habitlab_xcode_error "$2 must be one non-empty line"
}

habitlab_resolve_selector() {
    local system_path="$1"
    local injected_path="$2"
    local label="$3"
    local selected

    case "${HABITLAB_XCODE_PREFLIGHT_TEST_MODE:-0}" in
        0)
            [[ -z "${HABITLAB_XCODE_PREFLIGHT_TEST_XCRUN:-}" && \
                -z "${HABITLAB_XCODE_PREFLIGHT_TEST_XCODE_SELECT:-}" ]] || {
                habitlab_xcode_error "test tool injection requires explicit test mode"
                return
            }
            selected="$system_path"
            ;;
        1)
            selected="$injected_path"
            [[ "$selected" == /* ]] || {
                habitlab_xcode_error "test $label must be an absolute path"
                return
            }
            ;;
        *)
            habitlab_xcode_error "Xcode preflight test mode must be 0 or 1"
            return
            ;;
    esac
    [[ -n "$selected" && -x "$selected" ]] || {
        habitlab_xcode_error "$label is required"
        return
    }
    habitlab_canonicalize_file "$selected" || habitlab_xcode_error "the selected $label is unavailable"
}

habitlab_resolve_xcrun() {
    habitlab_resolve_selector \
        "$HABITLAB_SYSTEM_XCRUN" \
        "${HABITLAB_XCODE_PREFLIGHT_TEST_XCRUN:-}" \
        "xcrun"
}

habitlab_resolve_xcode_select() {
    habitlab_resolve_selector \
        "$HABITLAB_SYSTEM_XCODE_SELECT" \
        "${HABITLAB_XCODE_PREFLIGHT_TEST_XCODE_SELECT:-}" \
        "xcode-select"
}

habitlab_resolve_developer_dir() {
    local selected
    local xcode_select_bin

    if [[ -n "${DEVELOPER_DIR:-}" ]]; then
        selected="$DEVELOPER_DIR"
    else
        xcode_select_bin="$(habitlab_resolve_xcode_select)" || return
        selected="$("$xcode_select_bin" -p 2>/dev/null)" || {
            habitlab_xcode_error "could not resolve the selected developer directory"
            return
        }
    fi
    habitlab_require_single_line "$selected" "selected developer directory" || return
    habitlab_canonicalize_directory "$selected" || habitlab_xcode_error "the selected developer directory is unavailable"
}

habitlab_resolve_xcodebuild() {
    local developer_dir="$1"
    local xcrun_bin="$2"
    local selected

    selected="$(DEVELOPER_DIR="$developer_dir" "$xcrun_bin" --find xcodebuild 2>/dev/null)" || {
        habitlab_xcode_error "could not resolve xcodebuild from the selected developer directory"
        return
    }
    habitlab_require_single_line "$selected" "selected xcodebuild path" || return
    [[ "$selected" == /* ]] || {
        habitlab_xcode_error "selected xcodebuild path must be absolute"
        return
    }
    selected="$(habitlab_canonicalize_file "$selected")" || {
        habitlab_xcode_error "the selected xcodebuild path is unavailable"
        return
    }
    [[ -x "$selected" ]] || {
        habitlab_xcode_error "the selected Xcode does not provide an executable xcodebuild"
        return
    }
    printf '%s\n' "$selected"
}

habitlab_read_xcode_version() {
    local developer_dir="$1"
    local xcodebuild_bin="$2"
    local output
    local version

    output="$(DEVELOPER_DIR="$developer_dir" "$xcodebuild_bin" -version 2>&1)" || {
        habitlab_xcode_error "could not read the selected xcodebuild version"
        return
    }
    version="$(habitlab_parse_xcode_version "$output")"
    [[ -n "$version" ]] || {
        habitlab_xcode_error "could not parse the selected xcodebuild version"
        return
    }
    if ! habitlab_xcode_version_is_at_least \
        "$version" \
        "$HABITLAB_MIN_XCODE_MAJOR" \
        "$HABITLAB_MIN_XCODE_MINOR"; then
        habitlab_xcode_error \
            "Xcode $HABITLAB_MIN_XCODE_MAJOR.$HABITLAB_MIN_XCODE_MINOR or newer is required; found $version"
        return
    fi
    printf '%s\n' "$version"
}

habitlab_xcode_preflight() {
    local developer_dir
    local developer_identity_before
    local developer_identity_after
    local xcrun_bin
    local xcrun_identity
    local xcodebuild_bin
    local xcodebuild_identity_before
    local xcodebuild_identity_after
    local version

    HABITLAB_XCODE_DEVELOPER_DIR=""
    HABITLAB_XCODE_DEVELOPER_IDENTITY=""
    HABITLAB_XCRUN_BIN=""
    HABITLAB_XCRUN_IDENTITY=""
    HABITLAB_XCODEBUILD_BIN=""
    HABITLAB_XCODEBUILD_IDENTITY=""
    HABITLAB_XCODE_VERSION=""

    developer_dir="$(habitlab_resolve_developer_dir)" || return
    developer_identity_before="$(habitlab_file_identity "$developer_dir")" || {
        habitlab_xcode_error "could not identify the selected developer directory"
        return
    }
    xcrun_bin="$(habitlab_resolve_xcrun)" || return
    xcrun_identity="$(habitlab_file_identity "$xcrun_bin")" || {
        habitlab_xcode_error "could not identify the selected xcrun"
        return
    }
    xcodebuild_bin="$(habitlab_resolve_xcodebuild "$developer_dir" "$xcrun_bin")" || return
    xcodebuild_identity_before="$(habitlab_file_identity "$xcodebuild_bin")" || {
        habitlab_xcode_error "could not identify the selected xcodebuild"
        return
    }
    version="$(habitlab_read_xcode_version "$developer_dir" "$xcodebuild_bin")" || return
    developer_identity_after="$(habitlab_file_identity "$developer_dir")" || {
        habitlab_xcode_error "could not re-identify the selected developer directory"
        return
    }
    xcodebuild_identity_after="$(habitlab_file_identity "$xcodebuild_bin")" || {
        habitlab_xcode_error "could not re-identify the selected xcodebuild"
        return
    }
    [[ "$developer_identity_after" == "$developer_identity_before" ]] || {
        habitlab_xcode_error "selected developer directory identity changed during validation"
        return
    }
    [[ "$xcodebuild_identity_after" == "$xcodebuild_identity_before" ]] || {
        habitlab_xcode_error "selected xcodebuild identity changed during validation"
        return
    }

    HABITLAB_XCODE_DEVELOPER_DIR="$developer_dir"
    HABITLAB_XCODE_DEVELOPER_IDENTITY="$developer_identity_before"
    HABITLAB_XCRUN_BIN="$xcrun_bin"
    HABITLAB_XCRUN_IDENTITY="$xcrun_identity"
    HABITLAB_XCODEBUILD_BIN="$xcodebuild_bin"
    HABITLAB_XCODEBUILD_IDENTITY="$xcodebuild_identity_before"
    HABITLAB_XCODE_VERSION="$version"
    export DEVELOPER_DIR="$HABITLAB_XCODE_DEVELOPER_DIR"
}

habitlab_xcode_verify_pinned() {
    local current_developer_dir
    local current_developer_identity
    local current_xcrun_identity
    local current_xcodebuild
    local current_xcodebuild_identity
    local current_version

    [[ -n "${HABITLAB_XCODE_DEVELOPER_DIR:-}" && \
        -n "${HABITLAB_XCODE_DEVELOPER_IDENTITY:-}" && \
        -n "${HABITLAB_XCRUN_BIN:-}" && \
        -n "${HABITLAB_XCRUN_IDENTITY:-}" && \
        -n "${HABITLAB_XCODEBUILD_BIN:-}" && \
        -n "${HABITLAB_XCODEBUILD_IDENTITY:-}" && \
        -n "${HABITLAB_XCODE_VERSION:-}" ]] || {
        habitlab_xcode_error "Xcode preflight has not completed"
        return
    }
    current_developer_dir="$(habitlab_canonicalize_directory "$HABITLAB_XCODE_DEVELOPER_DIR")" || {
        habitlab_xcode_error "the pinned developer directory is unavailable"
        return
    }
    [[ "$current_developer_dir" == "$HABITLAB_XCODE_DEVELOPER_DIR" ]] || {
        habitlab_xcode_error "pinned developer directory path changed after validation"
        return
    }
    current_developer_identity="$(habitlab_file_identity "$current_developer_dir")" || {
        habitlab_xcode_error "could not identify the pinned developer directory"
        return
    }
    [[ "$current_developer_identity" == "$HABITLAB_XCODE_DEVELOPER_IDENTITY" ]] || {
        habitlab_xcode_error "pinned developer directory identity changed after validation"
        return
    }
    current_xcrun_identity="$(habitlab_file_identity "$HABITLAB_XCRUN_BIN")" || {
        habitlab_xcode_error "could not identify the pinned xcrun"
        return
    }
    [[ "$current_xcrun_identity" == "$HABITLAB_XCRUN_IDENTITY" ]] || {
        habitlab_xcode_error "pinned xcrun identity changed after validation"
        return
    }
    current_xcodebuild="$(habitlab_resolve_xcodebuild \
        "$HABITLAB_XCODE_DEVELOPER_DIR" \
        "$HABITLAB_XCRUN_BIN")" || return
    [[ "$current_xcodebuild" == "$HABITLAB_XCODEBUILD_BIN" ]] || {
        habitlab_xcode_error "xcrun-selected xcodebuild changed after validation"
        return
    }
    current_xcodebuild_identity="$(habitlab_file_identity "$current_xcodebuild")" || {
        habitlab_xcode_error "could not identify the pinned xcodebuild"
        return
    }
    [[ "$current_xcodebuild_identity" == "$HABITLAB_XCODEBUILD_IDENTITY" ]] || {
        habitlab_xcode_error "pinned xcodebuild identity changed after validation"
        return
    }
    current_version="$(habitlab_read_xcode_version \
        "$HABITLAB_XCODE_DEVELOPER_DIR" \
        "$HABITLAB_XCODEBUILD_BIN")" || return
    [[ "$current_version" == "$HABITLAB_XCODE_VERSION" ]] || {
        habitlab_xcode_error "pinned xcodebuild version changed after validation"
        return
    }
}

habitlab_xcode_run_xcodebuild() {
    habitlab_xcode_verify_pinned || return
    DEVELOPER_DIR="$HABITLAB_XCODE_DEVELOPER_DIR" "$HABITLAB_XCODEBUILD_BIN" "$@"
}

habitlab_xcode_run_xcrun() {
    habitlab_xcode_verify_pinned || return
    DEVELOPER_DIR="$HABITLAB_XCODE_DEVELOPER_DIR" "$HABITLAB_XCRUN_BIN" "$@"
}

habitlab_xcode_try_xcrun() {
    habitlab_xcode_verify_pinned || return
    DEVELOPER_DIR="$HABITLAB_XCODE_DEVELOPER_DIR" "$HABITLAB_XCRUN_BIN" "$@" || true
}

habitlab_xcode_run_external() {
    local executable="$1"
    shift

    habitlab_xcode_verify_pinned || return
    [[ "$executable" == /* && -x "$executable" ]] || {
        habitlab_xcode_error "pinned external command must be an absolute executable path"
        return
    }
    DEVELOPER_DIR="$HABITLAB_XCODE_DEVELOPER_DIR" "$executable" "$@"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    set -Eeuo pipefail
    export LC_ALL=C
    habitlab_xcode_preflight
    printf 'Xcode preflight passed: selected Xcode %s\n' "$HABITLAB_XCODE_VERSION"
fi
