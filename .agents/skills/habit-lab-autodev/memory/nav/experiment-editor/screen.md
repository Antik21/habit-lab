# Experiment Editor

**Destination and purpose.** `AppDestination.ExperimentEditor(experimentId: ExperimentId?)` edits a draft when non-null or creates one when null. The checked source revision is `1dfd34957075cccb6fca1cb1989e5610f56c8f3e`; the exact [reference flow contract](../../../../../../ui-tests/maestro/flows/reference-screens.yaml) was verified at that revision. This docs-only promotion successor does not modify the verified production/flow revision.

**Admission.** Terminal success: confirmed. Full owner gate: passed.

## Fixture, IDs, and paths

[`open-editor-from-details.yaml`](../../../flows/open-editor-from-details.yaml) starts from seeded `sleep-routine` Details and supplies its typed ID. [`open-create-editor.yaml`](../../../flows/open-create-editor.yaml) starts at List and supplies null. App-owned sequence: `habitlab.experiment-editor.screen.root`, `.action.back`, `.field.name`, `.action.metric`, `.state.metric.unset`, `.state.metric.daily-energy`, and `.action.save`. The expected picker result state and normal picker completion are the Editor root.

[`select-daily-energy.yaml`](../../../flows/select-daily-energy.yaml) opens `MetricPicker(experimentId)`, checks `habitlab.metric-picker.screen.root`, uses `.action.energy` (with `.action.sleep` and `.action.cancel` also closed), selects `MetricPickerResult.Selected(experimentId, DAILY_ENERGY)`, and verifies caller-scoped delivery with the Daily Energy state ID. [`back-to-details-from-editor.yaml`](../../../flows/back-to-details-from-editor.yaml) restores the editing Details caller. [`save-reference-draft-to-details.yaml`](../../../flows/save-reference-draft-to-details.yaml) creates a draft and replaces the null editor with its typed Details route.

## Evidence

Verification date: 2026-09-05. Independent reviewer: Codex; final verdict clean. Codex Manager supplied and checked gate results and visually inspected all 12 final screenshots; the independent reviewer also reported them sound. The full owner gate passed: `:buildSrc:test checkDocumentation :shared:check :androidApp:assembleDebug`, 58 connected-Android device tests, iOS simulator common tests, a preflight-wrapped native Xcode build, and `checkMaestroShell` for 15 subflows. Every final directory contains `command.log`, `report.xml`, three screenshots, `debug/maestro.log`, and `debug/commands-(reference-screens).json`; no filename contained `failure`, `error`, or `❌`.

- Android terminal-success, passing-gate evidence: source revision `1dfd34957075cccb6fca1cb1989e5610f56c8f3e`; `emulator-5554`, AVD `FO_Play_API36_1`, API 36; Maestro 2.6.1, JBR 21.0.11. Initial run ID `den-18-final-initial`: 1/1 success, 0 failures, 71s, `build/maestro/den-18-final-initial/android`. Repeat run ID `den-18-final-repeat`: 1/1 success, 0 failures, 74s, `build/maestro/den-18-final-repeat/android`.
- iOS terminal-success, passing-gate evidence: source revision `1dfd34957075cccb6fca1cb1989e5610f56c8f3e`; iPhone 17 Pro, iOS 26.5, UDID `19C4B36C-E2E9-43C3-BB33-B762FFDA5A08`; Xcode 26.6, Maestro 2.6.1, JBR 21.0.11. Initial run ID `den-18-final-initial`: 1/1 success, 0 failures, 44s, `build/maestro/den-18-final-initial/ios`. Repeat run ID `den-18-final-repeat`: 1/1 success, 0 failures, 43s, `build/maestro/den-18-final-repeat/ios`.

The final repeat reused the exact checked-in flow set later referenced by this node, with clear state and debug seed and without exploratory selector or path discovery. The iOS evidence applies only to the configured iOS 26.5 simulator.

## Invalidation

Re-verify if editor arguments, metric-result matching, save completion, or listed IDs/flows change. Validation, read, command, or missing-route errors do not establish the recorded completion.
