# UI automation

The current repository-owned UI baseline is the Maestro runner and common reference flow documented by the [toolchain catalog](../../../docs/01-stack-toolchain.md) and [verification policy](../../../docs/07-testing-verification.md). Use it when its existing contour matches the task. It is not a generic build/deploy, navigation, gate, or device-reservation adapter; do not copy, redirect, wrap, or modify it merely to fit AutoDev orchestration.

## Selector contract

For app-owned controls and assertions:

1. Use a stable semantic ID from the production automation contract.
2. If the shared ID is missing, add or correct it through the existing shared `AutomationId` plus `autodevId` contract in the owning UI when that is within task scope; do not introduce a plain `testTag` selector contract.
3. If an existing shared ID is not exposed, diagnose and fix the owning bridge for each requested platform or report the blocker.
4. If neither change is authorized, stop that assertion as `blocked` or `failed` with evidence.

Verify the corrected shared contract on every requested platform bridge. Verify both bridges only when both platforms are requested or the frozen scope explicitly requires the cross-platform bridge contract; do not expand a single-platform task merely to exercise an unrequested bridge.

Never use any accessibility-text or label selector for app-owned controls. Never fall back to localized text, runtime/user values, list position, visual matching that changes the contract, or screen coordinates. Coordinates are forbidden for app controls even when they seem stable.

Platform-only system back or edge gestures may use repository-owned platform subflows. Percentage coordinates are permitted only for the native iOS edge gesture identified by [ADR 0003](../../../../docs/adr/0003-maestro-cross-platform-ui-automation.md); do not generalize that exception.

## Evidence

Keep AutoDev-owned evidence indexes, checklist mappings, and report metadata under the configured ignored `.autodev/artifacts` root. Keep tool-owned canonical evidence at its documented location. The existing Maestro runner's accepted output is `build/maestro/<run-id>/<platform>`; reference those command logs, reports, screenshots, and debug output in place rather than copying them into `.autodev/` or changing the runner.

Evidence references must identify the source revision, target platform and virtual-device identifier, scenario/checklist assertion, command result, and time. Preserve relevant canonical output while preventing secrets or unrelated user data from entering it.

A screenshot alone does not prove an interaction. Pair visual evidence with command/report status and the expected semantic state ID. A broken selector is a product-contract or infrastructure finding, never permission for coordinate fallback.

Do not add reusable flows in this skill scaffold. If the existing reference flow does not cover the frozen acceptance path, use only task-scoped verification authorized by the current change and report the missing reusable adapter/flow boundary for its owning follow-up.
