# Screen navigation index

This is a compact discovery index, not an executable flow or proof of current reachability.

| Surface | Canonical source of truth | Reusable navigation record |
| --- | --- | --- |
| Production screens and dialogs | [`AutomationId.kt`](../../../../shared/src/commonMain/kotlin/com/denis/habitlab/shared/presentation/ui/automation/AutomationId.kt) and common navigation host | Future nodes under `nav/` following [`_SCHEMA.md`](nav/_SCHEMA.md) |
| Existing cross-platform reference contour | [`reference-screens.yaml`](../../../../ui-tests/maestro/flows/reference-screens.yaml) | Use the existing Maestro baseline; do not duplicate it here |
| Platform system gestures | [`flows/platform/`](../../../../ui-tests/maestro/flows/platform/) and [ADR 0003](../../../../docs/adr/0003-maestro-cross-platform-ui-automation.md) | Only the governed system gesture exception |

Before navigating, verify IDs against current production source. A memory record never overrides the code contract, current app state, or frozen task acceptance. Broken or missing IDs follow [UI automation](../references/ui-automation.md); they never authorize locale-, value-, position-, or coordinate-based app selectors.
