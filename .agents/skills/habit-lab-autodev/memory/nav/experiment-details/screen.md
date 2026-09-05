# Experiment Details

**Destination and purpose.** `AppDestination.Experiment(experimentId)` presents one typed experiment and starts editor, daily check-in, and delete paths. The checked source revision is `8452c8d08152b6d558fc31a08bd4d0223846940c`; the exact [reference flow contract](../../../../../../ui-tests/maestro/flows/reference-screens.yaml) was verified at that revision. This docs-only successor does not modify the verified source or flows.

**Admission.** Terminal success: confirmed. Full owner gate: passed.

## Fixture, IDs, and paths

Start from the seeded List and use [`open-daily-details.yaml`](../../../flows/open-daily-details.yaml) or [`open-sleep-details.yaml`](../../../flows/open-sleep-details.yaml). The fixed row supplies the `ExperimentId`; the Details route remains the caller for all typed descendants. App-owned sequence: `habitlab.experiment-details.screen.root`, `.action.back`, `.action.edit`, `.action.check-in`, and `.action.delete`. The expected normal completion is the Details root; [`back-to-list-from-details.yaml`](../../../flows/back-to-list-from-details.yaml) returns to List.

For the `sleep-routine` draft, [`open-editor-from-details.yaml`](../../../flows/open-editor-from-details.yaml) opens `ExperimentEditor(experimentId)` and [`back-to-details-from-editor.yaml`](../../../flows/back-to-details-from-editor.yaml) restores this caller. Check-in opens `DailyCheckIn(experimentId, CheckInRouteDate)` with the current typed date through [`open-daily-check-in-from-details.yaml`](../../../flows/open-daily-check-in-from-details.yaml); toolbar back and save return here through [`back-to-details-from-daily-check-in.yaml`](../../../flows/back-to-details-from-daily-check-in.yaml) and [`record-daily-check-in.yaml`](../../../flows/record-daily-check-in.yaml).

Delete opens `ConfirmDelete(experimentId)` at `habitlab.confirm-delete.screen.root` with `.action.cancel` and `.action.confirm`. [`cancel-delete-from-details.yaml`](../../../flows/cancel-delete-from-details.yaml) delivers cancellation to this Details caller; [`confirm-delete-to-list.yaml`](../../../flows/confirm-delete-to-list.yaml) deletes the flow-created draft and returns List, preserving both fixed rows.

Android and iOS are supported; Details flows have no platform-owned gesture and use app-owned return IDs.

## Evidence

Verification date: 2026-09-05. Independent reviewer: Codex; final verdict clean. Codex Manager supplied and checked gate results and visually inspected all 12 review screenshots; the independent reviewer also reported them sound. The full owner gate passed: `:buildSrc:test checkDocumentation :shared:check :androidApp:assembleDebug`, 58 connected-Android device tests, iOS simulator common tests, a preflight-wrapped native Xcode build, and `checkMaestroShell` for 15 subflows. Every review directory contains `command.log`, `report.xml`, three screenshots, `debug/maestro.log`, and `debug/commands-(reference-screens).json`; no filename contained `failure`, `error`, or `❌`.

- Android terminal-success, passing-gate evidence: source revision `8452c8d08152b6d558fc31a08bd4d0223846940c`; `emulator-5554`, AVD `FO_Play_API36_1`, API 36; Maestro 2.6.1, JBR 21.0.11. Initial run ID `den-18-review-initial`: 1/1 success, 0 failures, 73s, `build/maestro/den-18-review-initial/android`. Repeat run ID `den-18-review-repeat`: 1/1 success, 0 failures, 73s, `build/maestro/den-18-review-repeat/android`.
- iOS terminal-success, passing-gate evidence: source revision `8452c8d08152b6d558fc31a08bd4d0223846940c`; iPhone 17 Pro, iOS 26.5, UDID `19C4B36C-E2E9-43C3-BB33-B762FFDA5A08`; Xcode 26.6, Maestro 2.6.1, JBR 21.0.11. Initial run ID `den-18-review-initial`: 1/1 success, 0 failures, 47s, `build/maestro/den-18-review-initial/ios`. Repeat run ID `den-18-review-repeat`: 1/1 success, 0 failures, 47s, `build/maestro/den-18-review-repeat/ios`.

The review repeat reused the exact checked-in flow set later referenced by this node, with clear state and debug seed and no exploratory selector or path discovery. This iOS evidence is limited to iOS 26.5, not iOS 16.

## Invalidation

Re-verify for changes to experiment route arguments, Details actions, caller-scoped delete delivery, or linked flows. Loading/error completion, a mismatched caller ID, or a fixed-seed deletion invalidates this record.
