# Screen navigation index

This is a compact discovery index, not proof of current reachability. Canonical executable navigation paths are reviewed skill-owned Maestro flows; linked candidate records await the full owner gate before becoming nodes.

| Surface | Canonical source of truth | Candidate evidence record |
| --- | --- | --- |
| Experiment List | [`ExperimentListScreen.kt`](../../../../shared/src/commonMain/kotlin/com/denis/habitlab/shared/presentation/experimentlist/ExperimentListScreen.kt) | [screen](nav/experiment-list/screen.md) · [logic](nav/experiment-list/logic.md) |
| Experiment Details and delete dialog | [`ExperimentDetailsScreen.kt`](../../../../shared/src/commonMain/kotlin/com/denis/habitlab/shared/presentation/experimentdetails/ExperimentDetailsScreen.kt) | [screen](nav/experiment-details/screen.md) · [logic](nav/experiment-details/logic.md) |
| Experiment Editor and metric picker | [`ExperimentEditorScreen.kt`](../../../../shared/src/commonMain/kotlin/com/denis/habitlab/shared/presentation/experimenteditor/ExperimentEditorScreen.kt) | [screen](nav/experiment-editor/screen.md) · [logic](nav/experiment-editor/logic.md) |
| Daily Check-In | [`DailyCheckInScreen.kt`](../../../../shared/src/commonMain/kotlin/com/denis/habitlab/shared/presentation/dailycheckin/DailyCheckInScreen.kt) | [screen](nav/daily-check-in/screen.md) · [logic](nav/daily-check-in/logic.md) |
| Settings | [`SettingsScreen.kt`](../../../../shared/src/commonMain/kotlin/com/denis/habitlab/shared/presentation/settings/SettingsScreen.kt) | [screen](nav/settings/screen.md) · [logic](nav/settings/logic.md) |
| Existing cross-platform reference contour | [`reference-screens.yaml`](../../../../ui-tests/maestro/flows/reference-screens.yaml) | The sole runner-owned scenario composes the reviewed [skill-owned flows](../flows/) |
| Reusable route records | [`flows/`](../flows/) | Narrow DEN-18 paths with semantic start/end assertions; linked candidates await owner-gate promotion |
| Platform system gestures | [`flows/platform/`](../../../../ui-tests/maestro/flows/platform/) and [ADR 0003](../../../../docs/adr/0003-maestro-cross-platform-ui-automation.md) | Only the governed system gesture exception |

Before navigating, verify IDs against current production source. Neither a flow nor a memory record overrides the code contract, current app state, or frozen task acceptance. Broken or missing IDs follow [UI automation](../references/ui-automation.md); they never authorize locale-, value-, position-, or coordinate-based app selectors.
