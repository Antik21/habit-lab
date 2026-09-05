# Experiment Details

**Destination and purpose.** `AppDestination.Experiment(experimentId)` presents one typed experiment and starts editor, daily check-in, and delete paths. The checked source revision is `1dfd34957075cccb6fca1cb1989e5610f56c8f3e`; the exact [reference flow contract](../../../../../../ui-tests/maestro/flows/reference-screens.yaml) was verified at that revision. This docs-only promotion successor does not modify the verified production/flow revision.

**Admission.** Terminal success: confirmed. Full owner gate: passed.

## Fixture, IDs, and paths

Start from the seeded List and use [`open-daily-details.yaml`](../../../flows/open-daily-details.yaml) or [`open-sleep-details.yaml`](../../../flows/open-sleep-details.yaml). The fixed row supplies the `ExperimentId`; the Details route remains the caller for all typed descendants. App-owned sequence: `habitlab.experiment-details.screen.root`, `.action.back`, `.action.edit`, `.action.check-in`, and `.action.delete`. The expected normal completion is the Details root; [`back-to-list-from-details.yaml`](../../../flows/back-to-list-from-details.yaml) returns to List.

For the `sleep-routine` draft, [`open-editor-from-details.yaml`](../../../flows/open-editor-from-details.yaml) opens `ExperimentEditor(experimentId)` and [`back-to-details-from-editor.yaml`](../../../flows/back-to-details-from-editor.yaml) restores this caller. Check-in opens `DailyCheckIn(experimentId, CheckInRouteDate)` with the current typed date through [`open-daily-check-in-from-details.yaml`](../../../flows/open-daily-check-in-from-details.yaml); toolbar back and save return here through [`back-to-details-from-daily-check-in.yaml`](../../../flows/back-to-details-from-daily-check-in.yaml) and [`record-daily-check-in.yaml`](../../../flows/record-daily-check-in.yaml).

Delete opens `ConfirmDelete(experimentId)` at `habitlab.confirm-delete.screen.root` with `.action.cancel` and `.action.confirm`. [`cancel-delete-from-details.yaml`](../../../flows/cancel-delete-from-details.yaml) delivers cancellation to this Details caller; [`confirm-delete-to-list.yaml`](../../../flows/confirm-delete-to-list.yaml) deletes the flow-created draft and returns List, preserving both fixed rows.

## Evidence

Verification date: 2026-09-05. Independent reviewer: Codex; final verdict clean. Codex Manager supplied and checked gate results and visually inspected all 12 final screenshots; the independent reviewer also reported them sound. The full owner gate passed: `:buildSrc:test checkDocumentation :shared:check :androidApp:assembleDebug`, 58 connected-Android device tests, iOS simulator common tests, a preflight-wrapped native Xcode build, and `checkMaestroShell` for 15 subflows. Every final directory contains `command.log`, `report.xml`, three screenshots, `debug/maestro.log`, and `debug/commands-(reference-screens).json`; no filename contained `failure`, `error`, or `❌`.

- Android terminal-success, passing-gate evidence: source revision `1dfd34957075cccb6fca1cb1989e5610f56c8f3e`; `emulator-5554`, AVD `FO_Play_API36_1`, API 36; Maestro 2.6.1, JBR 21.0.11. Initial run ID `den-18-final-initial`: 1/1 success, 0 failures, 71s, `build/maestro/den-18-final-initial/android`. Repeat run ID `den-18-final-repeat`: 1/1 success, 0 failures, 74s, `build/maestro/den-18-final-repeat/android`.
- iOS terminal-success, passing-gate evidence: source revision `1dfd34957075cccb6fca1cb1989e5610f56c8f3e`; iPhone 17 Pro, iOS 26.5, UDID `19C4B36C-E2E9-43C3-BB33-B762FFDA5A08`; Xcode 26.6, Maestro 2.6.1, JBR 21.0.11. Initial run ID `den-18-final-initial`: 1/1 success, 0 failures, 44s, `build/maestro/den-18-final-initial/ios`. Repeat run ID `den-18-final-repeat`: 1/1 success, 0 failures, 43s, `build/maestro/den-18-final-repeat/ios`.

The final repeat reused the exact checked-in flow set later referenced by this node, with clear state and debug seed and no exploratory selector or path discovery. This iOS evidence is limited to iOS 26.5, not iOS 16.

## Invalidation

Re-verify for changes to experiment route arguments, Details actions, caller-scoped delete delivery, or linked flows. Loading/error completion, a mismatched caller ID, or a fixed-seed deletion invalidates this record.
