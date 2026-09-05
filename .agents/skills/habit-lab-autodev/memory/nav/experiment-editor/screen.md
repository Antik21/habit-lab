# Candidate evidence: Experiment Editor

**Destination and purpose.** `AppDestination.ExperimentEditor(experimentId: ExperimentId?)` edits a draft when non-null or creates one when null. The checked source revision is `f479db59f86b3894c316ca69b251f3a29c3af9ce`; the exact [reference flow contract](../../../../../../ui-tests/maestro/flows/reference-screens.yaml) was verified at that revision. This evidence record was added afterward and does not modify the verified production/flow revision.

**Admission.** Owner gate: pending; not yet admissible as terminal reusable memory. Promotion requires the manager's factual full passing-gate status.

## Fixture, IDs, and paths

[`open-editor-from-details.yaml`](../../../flows/open-editor-from-details.yaml) starts from seeded `sleep-routine` Details and supplies its typed ID. [`open-create-editor.yaml`](../../../flows/open-create-editor.yaml) starts at List and supplies null. App-owned sequence: `habitlab.experiment-editor.screen.root`, `.action.back`, `.field.name`, `.action.metric`, `.state.metric.unset`, `.state.metric.daily-energy`, and `.action.save`. The expected picker result state and normal picker completion are the Editor root.

[`select-daily-energy.yaml`](../../../flows/select-daily-energy.yaml) opens `MetricPicker(experimentId)`, checks `habitlab.metric-picker.screen.root`, uses `.action.energy` (with `.action.sleep` and `.action.cancel` also closed), selects `MetricPickerResult.Selected(experimentId, DAILY_ENERGY)`, and verifies caller-scoped delivery with the Daily Energy state ID. [`back-to-details-from-editor.yaml`](../../../flows/back-to-details-from-editor.yaml) restores the editing Details caller. [`save-reference-draft-to-details.yaml`](../../../flows/save-reference-draft-to-details.yaml) creates a draft and replaces the null editor with its typed Details route.

## Evidence

Verification date: 2026-09-05. Reviewer: Codex Manager checked JUnit and command/debug/trace output and visually inspected all six initial screenshots. Every initial and repeat directory contains `command.log`, `report.xml`, three screenshots, `debug/maestro.log`, and `debug/commands-(reference-screens).json`; no filename contained `failure`, `error`, or `❌`.

- Android — `emulator-5554`, AVD `FO_Play_API36_1`, API 36; Maestro 2.6.1, JBR 21.0.11. Initial run ID `den-18-initial`: 1/1 success, 0 failures, 72s, `build/maestro/den-18-initial/android`. Repeat run ID `den-18-repeat`: 1/1 success, 0 failures, 73s, `build/maestro/den-18-repeat/android`.
- iOS — iPhone 17 Pro, iOS 26.5, UDID `19C4B36C-E2E9-43C3-BB33-B762FFDA5A08`; Xcode 26.6, Maestro 2.6.1, JBR 21.0.11. Initial run ID `den-18-initial`: 1/1 success, 0 failures, 44s, `build/maestro/den-18-initial/ios`. Repeat run ID `den-18-repeat`: 1/1 success, 0 failures, 44s, `build/maestro/den-18-repeat/ios`.

Repeat reused the exact checked-in flow set later referenced by this candidate, with clear state and debug seed and without exploratory selector or path discovery. The iOS evidence applies only to the configured iOS 26.5 simulator.

## Invalidation

Re-verify if editor arguments, metric-result matching, save completion, or listed IDs/flows change. Validation, read, command, or missing-route errors do not establish the recorded completion.
