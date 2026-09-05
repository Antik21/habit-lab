# Daily Check-In

**Destination and purpose.** `AppDestination.DailyCheckIn(experimentId, localDate: CheckInRouteDate)` records one selected outcome for a typed experiment/date pair. The checked source revision is `8452c8d08152b6d558fc31a08bd4d0223846940c`; the exact [reference flow contract](../../../../../../ui-tests/maestro/flows/reference-screens.yaml) was verified at that revision. This docs-only successor does not modify the verified source or flows.

**Admission.** Terminal success: confirmed. Full owner gate: passed.

## Fixture, IDs, and paths

Start on seeded `daily-movement` Details. That caller emits the typed `ExperimentId` and current local date; the host converts the latter to `CheckInRouteDate`. [`open-daily-check-in-from-details.yaml`](../../../flows/open-daily-check-in-from-details.yaml) uses `habitlab.experiment-details.action.check-in`, then checks `habitlab.daily-check-in.screen.root`, `habitlab.daily-check-in.action.performed`, and `habitlab.daily-check-in.action.skipped`. Its expected completion is Daily Check-In.

[`back-to-details-from-daily-check-in.yaml`](../../../flows/back-to-details-from-daily-check-in.yaml) taps `habitlab.daily-check-in.action.back` and verifies toolbar back restores Details. [`record-daily-check-in.yaml`](../../../flows/record-daily-check-in.yaml) re-enters, taps `habitlab.daily-check-in.action.skipped`, verifies `habitlab.daily-check-in.state.outcome.skipped`, taps `habitlab.daily-check-in.action.save`, and returns Details. There is no dialog result on this path.

## Evidence

Verification date: 2026-09-05. Independent reviewer: Codex; final verdict clean. Codex Manager supplied and checked gate results and visually inspected all 12 review screenshots; the independent reviewer also reported them sound. The full owner gate passed: `:buildSrc:test checkDocumentation :shared:check :androidApp:assembleDebug`, 58 connected-Android device tests, iOS simulator common tests, a preflight-wrapped native Xcode build, and `checkMaestroShell` for 15 subflows. Every review directory contains `command.log`, `report.xml`, three screenshots, `debug/maestro.log`, and `debug/commands-(reference-screens).json`; no filename contained `failure`, `error`, or `❌`.

- Android terminal-success, passing-gate evidence: source revision `8452c8d08152b6d558fc31a08bd4d0223846940c`; `emulator-5554`, AVD `FO_Play_API36_1`, API 36; Maestro 2.6.1, JBR 21.0.11. Initial run ID `den-18-review-initial`: 1/1 success, 0 failures, 73s, `build/maestro/den-18-review-initial/android`. Repeat run ID `den-18-review-repeat`: 1/1 success, 0 failures, 73s, `build/maestro/den-18-review-repeat/android`.
- iOS terminal-success, passing-gate evidence: source revision `8452c8d08152b6d558fc31a08bd4d0223846940c`; iPhone 17 Pro, iOS 26.5, UDID `19C4B36C-E2E9-43C3-BB33-B762FFDA5A08`; Xcode 26.6, Maestro 2.6.1, JBR 21.0.11. Initial run ID `den-18-review-initial`: 1/1 success, 0 failures, 47s, `build/maestro/den-18-review-initial/ios`. Repeat run ID `den-18-review-repeat`: 1/1 success, 0 failures, 47s, `build/maestro/den-18-review-repeat/ios`.

The review repeat reused the exact checked-in flow set later referenced by this node, with clear state and debug seed and no exploratory selector or path discovery. This iOS evidence does not prove the iOS 16 minimum runtime.

## Invalidation

Re-verify after route-date, outcome/save/back behavior, IDs, or linked flows change. A missing experiment, invalid date, read error, or command error invalidates the recorded success path.
