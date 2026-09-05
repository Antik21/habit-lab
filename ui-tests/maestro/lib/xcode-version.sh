#!/usr/bin/env bash

parse_xcode_version() {
    printf '%s\n' "$1" |
        sed -n '1s/^Xcode \([0-9][0-9]*\)\.\([0-9][0-9]*\).*$/\1.\2/p'
}

xcode_version_is_at_least() {
    local actual_version="$1"
    local required_major="$2"
    local required_minor="$3"
    local actual_major
    local actual_minor

    case "$actual_version" in
        [0-9]*.[0-9]*) ;;
        *) return 2 ;;
    esac

    actual_major="${actual_version%%.*}"
    actual_minor="${actual_version#*.}"
    case "$actual_major:$actual_minor:$required_major:$required_minor" in
        *[!0-9:]*) return 2 ;;
    esac

    if (( actual_major > required_major )); then
        return 0
    fi
    if (( actual_major < required_major )); then
        return 1
    fi
    (( actual_minor >= required_minor ))
}
