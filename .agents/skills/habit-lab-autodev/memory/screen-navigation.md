# Screen navigation index

This is a compact discovery index, not proof of current reachability. Canonical executable navigation paths are reviewed skill-owned Maestro flows; each linked `screen.md` is an admitted node with terminal-success, passing-gate evidence, while `logic.md` is supporting logic only.

| Surface | Canonical source of truth | Admitted screen node / supporting logic |
| --- | --- | --- |
| Experiment List | [`ExperimentListScreen.kt`](../../../../shared/src/commonMain/kotlin/com/denis/habitlab/shared/presentation/experimentlist/ExperimentListScreen.kt) | [screen.md — admitted node](nav/experiment-list/screen.md) · [logic.md — supporting logic](nav/experiment-list/logic.md) |
| Experiment Details and delete dialog | [`ExperimentDetailsScreen.kt`](../../../../shared/src/commonMain/kotlin/com/denis/habitlab/shared/presentation/experimentdetails/ExperimentDetailsScreen.kt) | [screen.md — admitted node](nav/experiment-details/screen.md) · [logic.md — supporting logic](nav/experiment-details/logic.md) |
| Experiment Editor and metric picker | [`ExperimentEditorScreen.kt`](../../../../shared/src/commonMain/kotlin/com/denis/habitlab/shared/presentation/experimenteditor/ExperimentEditorScreen.kt) | [screen.md — admitted node](nav/experiment-editor/screen.md) · [logic.md — supporting logic](nav/experiment-editor/logic.md) |
| Daily Check-In | [`DailyCheckInScreen.kt`](../../../../shared/src/commonMain/kotlin/com/denis/habitlab/shared/presentation/dailycheckin/DailyCheckInScreen.kt) | [screen.md — admitted node](nav/daily-check-in/screen.md) · [logic.md — supporting logic](nav/daily-check-in/logic.md) |
| Settings | [`SettingsScreen.kt`](../../../../shared/src/commonMain/kotlin/com/denis/habitlab/shared/presentation/settings/SettingsScreen.kt) | [screen.md — admitted node](nav/settings/screen.md) · [logic.md — supporting logic](nav/settings/logic.md) |
| Existing cross-platform reference contour | [`reference-screens.yaml`](../../../../ui-tests/maestro/flows/reference-screens.yaml) | The sole runner-owned scenario composes the reviewed [skill-owned flows](../flows/) |
| Reusable route records | [`flows/`](../flows/) | Narrow DEN-18 paths with semantic start/end assertions and linked admitted nodes |
| Platform system gestures | [`flows/platform/`](../../../../ui-tests/maestro/flows/platform/) and [ADR 0003](../../../../docs/adr/0003-maestro-cross-platform-ui-automation.md) | Only the governed system gesture exception |
| Progressive selection and receipt | [self-learning reference](../references/self-learning.md) and [`catalog.json`](catalog.json) | Local-history discovery only; ledger/receipt bind actual reads |

Before navigating, verify IDs against current production source. Neither a flow nor a memory record overrides the code contract, current app state, or frozen task acceptance. Broken or missing IDs follow [UI automation](../references/ui-automation.md); they never authorize locale-, value-, position-, or coordinate-based app selectors.
