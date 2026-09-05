#!/usr/bin/env bash

set -Eeuo pipefail

readonly TEST_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

"$BASH" "$TEST_DIR/navigation-flows-test.sh"
"$BASH" "$TEST_DIR/xcode-preflight-test.sh"

PYTHON_BIN="$(command -v python3 || true)"
[[ -n "$PYTHON_BIN" ]] || {
    printf 'FAIL: Python 3.9+ is required for the AutoDev gate contract test, but python3 is unavailable\n' >&2
    exit 1
}
readonly PYTHON_BIN
if ! "$PYTHON_BIN" -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 9) else 1)'; then
    printf 'FAIL: Python 3.9+ is required for the AutoDev gate contract test; found %s\n' \
        "$("$PYTHON_BIN" --version 2>&1 || true)" >&2
    exit 1
fi
"$PYTHON_BIN" "$TEST_DIR/autodev-gate-test.py"
