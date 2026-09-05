# Settings

**Destination and purpose.** `AppDestination.Settings` is entered only from Gallery and presents theme selection plus a toolbar back control. The checked source revision is `1dfd34957075cccb6fca1cb1989e5610f56c8f3e`; the exact [reference flow contract](../../../../../../ui-tests/maestro/flows/reference-screens.yaml) was verified at that revision. This docs-only promotion successor does not modify the verified production/flow revision.

**Admission.** Terminal success: confirmed. Full owner gate: passed.

## Fixture, IDs, and paths

Start at the debug-seeded List root and run [`open-settings.yaml`](../../../flows/open-settings.yaml). App-owned sequence is `habitlab.experiment-list.screen.root`, `habitlab.experiment-list.action.open-settings`, `habitlab.settings.screen.root`, `habitlab.settings.action.back`, and the closed theme IDs. The expected Settings completion is its screen root.

The platform return is intentionally limited to the existing [`android-system-back.yaml`](../../../../../../ui-tests/maestro/flows/platform/android-system-back.yaml) or [`ios-edge-back.yaml`](../../../../../../ui-tests/maestro/flows/platform/ios-edge-back.yaml), each ending at List. The iOS flow's leading-edge swipe is the sole coordinate exception; no app control is selected by coordinate. Settings has no typed child argument or dialog result.

## Evidence

Verification date: 2026-09-05. Independent reviewer: Codex; final verdict clean. Codex Manager supplied and checked gate results and visually inspected all 12 final screenshots; the independent reviewer also reported them sound. The full owner gate passed: `:buildSrc:test checkDocumentation :shared:check :androidApp:assembleDebug`, 58 connected-Android device tests, iOS simulator common tests, a preflight-wrapped native Xcode build, and `checkMaestroShell` for 15 subflows. Every final directory contains `command.log`, `report.xml`, three screenshots, `debug/maestro.log`, and `debug/commands-(reference-screens).json`; no filename contained `failure`, `error`, or `❌`.

- Android terminal-success, passing-gate evidence: source revision `1dfd34957075cccb6fca1cb1989e5610f56c8f3e`; `emulator-5554`, AVD `FO_Play_API36_1`, API 36; Maestro 2.6.1, JBR 21.0.11. Initial run ID `den-18-final-initial`: 1/1 success, 0 failures, 71s, `build/maestro/den-18-final-initial/android`. Repeat run ID `den-18-final-repeat`: 1/1 success, 0 failures, 74s, `build/maestro/den-18-final-repeat/android`.
- iOS terminal-success, passing-gate evidence: source revision `1dfd34957075cccb6fca1cb1989e5610f56c8f3e`; iPhone 17 Pro, iOS 26.5, UDID `19C4B36C-E2E9-43C3-BB33-B762FFDA5A08`; Xcode 26.6, Maestro 2.6.1, JBR 21.0.11. Initial run ID `den-18-final-initial`: 1/1 success, 0 failures, 44s, `build/maestro/den-18-final-initial/ios`. Repeat run ID `den-18-final-repeat`: 1/1 success, 0 failures, 43s, `build/maestro/den-18-final-repeat/ios`.

The final repeat reused the exact checked-in flow set later referenced by this node, with clear state and debug seed and no exploratory selector or path discovery. This iOS evidence is only for the configured iOS 26.5 simulator, not iOS 16.

## Invalidation

Re-verify if Settings routing, system-back subflows, semantic IDs, or theme selection/process-local runtime preference behavior changes. A platform gesture that remains on Settings or a non-List completion invalidates this record.
