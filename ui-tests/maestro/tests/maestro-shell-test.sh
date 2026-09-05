#!/usr/bin/env bash

set -Eeuo pipefail

readonly TEST_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

"$BASH" "$TEST_DIR/navigation-flows-test.sh"
"$BASH" "$TEST_DIR/xcode-preflight-test.sh"
