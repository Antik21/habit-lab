# Screen navigation index

This is a compact discovery index, not proof of current reachability. Canonical executable navigation paths are reviewed skill-owned Maestro flows; evidence-backed nodes remain a separate record.

| Surface | Canonical source of truth | Reusable navigation record |
| --- | --- | --- |
| Production screens and dialogs | [`AutomationId.kt`](../../../../shared/src/commonMain/kotlin/com/denis/habitlab/shared/presentation/ui/automation/AutomationId.kt) and common navigation host | Future nodes under `nav/` following [`_SCHEMA.md`](nav/_SCHEMA.md) |
| Existing cross-platform reference contour | [`reference-screens.yaml`](../../../../ui-tests/maestro/flows/reference-screens.yaml) | The sole runner-owned scenario composes the reviewed [skill-owned flows](../flows/) |
| Reusable route records | [`flows/`](../flows/) | Narrow DEN-18 paths with semantic start/end assertions; navigation nodes await platform evidence |
| Platform system gestures | [`flows/platform/`](../../../../ui-tests/maestro/flows/platform/) and [ADR 0003](../../../../docs/adr/0003-maestro-cross-platform-ui-automation.md) | Only the governed system gesture exception |

Before navigating, verify IDs against current production source. Neither a flow nor a memory record overrides the code contract, current app state, or frozen task acceptance. Broken or missing IDs follow [UI automation](../references/ui-automation.md); they never authorize locale-, value-, position-, or coordinate-based app selectors.
