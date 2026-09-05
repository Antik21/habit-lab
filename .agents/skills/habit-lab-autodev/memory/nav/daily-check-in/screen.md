# Daily Check-In

**Destination and purpose.** `AppDestination.DailyCheckIn(experimentId, localDate: CheckInRouteDate)` records one selected outcome for a typed experiment/date pair. The checked source revision is `1dfd34957075cccb6fca1cb1989e5610f56c8f3e`; the exact [reference flow contract](../../../../../../ui-tests/maestro/flows/reference-screens.yaml) was verified at that revision. This docs-only promotion successor does not modify the verified production/flow revision.

**Admission.** Terminal success: confirmed. Full owner gate: passed.

## Fixture, IDs, and paths

Start on seeded `daily-movement` Details. That caller emits the typed `ExperimentId` and current local date; the host converts the latter to `CheckInRouteDate`. [`open-daily-check-in-from-details.yaml`](../../../flows/open-daily-check-in-from-details.yaml) uses `habitlab.experiment-details.action.check-in`, then checks `habitlab.daily-check-in.screen.root`, `.action.performed`, and `.action.skipped`. Its expected completion is Daily Check-In.

[`back-to-details-from-daily-check-in.yaml`](../../../flows/back-to-details-from-daily-check-in.yaml) verifies toolbar back restores Details. [`record-daily-check-in.yaml`](../../../flows/record-daily-check-in.yaml) re-enters, selects the closed skipped-outcome control, verifies `habitlab.daily-check-in.state.outcome.skipped`, saves, and returns Details. There is no dialog result on this path.

## Evidence

Verification date: 2026-09-05. Independent reviewer: Codex; final verdict clean. Codex Manager supplied and checked gate results and visually inspected all 12 final screenshots; the independent reviewer also reported them sound. The full owner gate passed: `:buildSrc:test checkDocumentation :shared:check :androidApp:assembleDebug`, 58 connected-Android device tests, iOS simulator common tests, a preflight-wrapped native Xcode build, and `checkMaestroShell` for 15 subflows. Every final directory contains `command.log`, `report.xml`, three screenshots, `debug/maestro.log`, and `debug/commands-(reference-screens).json`; no filename contained `failure`, `error`, or `❌`.

- Android terminal-success, passing-gate evidence: source revision `1dfd34957075cccb6fca1cb1989e5610f56c8f3e`; `emulator-5554`, AVD `FO_Play_API36_1`, API 36; Maestro 2.6.1, JBR 21.0.11. Initial run ID `den-18-final-initial`: 1/1 success, 0 failures, 71s, `build/maestro/den-18-final-initial/android`. Repeat run ID `den-18-final-repeat`: 1/1 success, 0 failures, 74s, `build/maestro/den-18-final-repeat/android`.
- iOS terminal-success, passing-gate evidence: source revision `1dfd34957075cccb6fca1cb1989e5610f56c8f3e`; iPhone 17 Pro, iOS 26.5, UDID `19C4B36C-E2E9-43C3-BB33-B762FFDA5A08`; Xcode 26.6, Maestro 2.6.1, JBR 21.0.11. Initial run ID `den-18-final-initial`: 1/1 success, 0 failures, 44s, `build/maestro/den-18-final-initial/ios`. Repeat run ID `den-18-final-repeat`: 1/1 success, 0 failures, 43s, `build/maestro/den-18-final-repeat/ios`.

The final repeat reused the exact checked-in flow set later referenced by this node, with clear state and debug seed and no exploratory selector or path discovery. This iOS evidence does not prove the iOS 16 minimum runtime.

## Invalidation

Re-verify after route-date, outcome/save/back behavior, IDs, or linked flows change. A missing experiment, invalid date, read error, or command error invalidates the recorded success path.
