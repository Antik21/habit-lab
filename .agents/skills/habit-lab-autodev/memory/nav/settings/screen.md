# Settings

**Destination and purpose.** `AppDestination.Settings` is entered from Experiment List, whose stable wire route remains `AppDestination.Gallery`, and presents theme selection plus a toolbar back control. The checked source revision is `8452c8d08152b6d558fc31a08bd4d0223846940c`; the exact [reference flow contract](../../../../../../ui-tests/maestro/flows/reference-screens.yaml) was verified at that revision. This docs-only successor does not modify the verified source or flows.

**Admission.** Terminal success: confirmed. Full owner gate: passed.

## Fixture, IDs, and paths

Start at the debug-seeded List root and run [`open-settings.yaml`](../../../flows/open-settings.yaml). App-owned sequence is `habitlab.experiment-list.screen.root`, `habitlab.experiment-list.action.open-settings`, `habitlab.settings.screen.root`, `habitlab.settings.action.back`, `habitlab.settings.theme.system`, `habitlab.settings.theme.light`, and `habitlab.settings.theme.dark`. The expected open-flow completion is the Settings root; its toolbar back is the app-owned `habitlab.settings.action.back` path.

Separately, the platform return is limited to [`android-system-back.yaml`](../../../../../../ui-tests/maestro/flows/platform/android-system-back.yaml) or [`ios-edge-back.yaml`](../../../../../../ui-tests/maestro/flows/platform/ios-edge-back.yaml), each starting at Settings and completing at List. The iOS flow's leading-edge swipe is the sole coordinate exception; no app control is selected by coordinate. Settings has no typed child argument or dialog result.

## Evidence

Verification date: 2026-09-05. Independent reviewer: Codex; final verdict clean. Codex Manager supplied and checked gate results and visually inspected all 12 review screenshots; the independent reviewer also reported them sound. The full owner gate passed: `:buildSrc:test checkDocumentation :shared:check :androidApp:assembleDebug`, 58 connected-Android device tests, iOS simulator common tests, a preflight-wrapped native Xcode build, and `checkMaestroShell` for 15 subflows. Every review directory contains `command.log`, `report.xml`, three screenshots, `debug/maestro.log`, and `debug/commands-(reference-screens).json`; no filename contained `failure`, `error`, or `❌`.

- Android terminal-success, passing-gate evidence: source revision `8452c8d08152b6d558fc31a08bd4d0223846940c`; `emulator-5554`, AVD `FO_Play_API36_1`, API 36; Maestro 2.6.1, JBR 21.0.11. Initial run ID `den-18-review-initial`: 1/1 success, 0 failures, 73s, `build/maestro/den-18-review-initial/android`. Repeat run ID `den-18-review-repeat`: 1/1 success, 0 failures, 73s, `build/maestro/den-18-review-repeat/android`.
- iOS terminal-success, passing-gate evidence: source revision `8452c8d08152b6d558fc31a08bd4d0223846940c`; iPhone 17 Pro, iOS 26.5, UDID `19C4B36C-E2E9-43C3-BB33-B762FFDA5A08`; Xcode 26.6, Maestro 2.6.1, JBR 21.0.11. Initial run ID `den-18-review-initial`: 1/1 success, 0 failures, 47s, `build/maestro/den-18-review-initial/ios`. Repeat run ID `den-18-review-repeat`: 1/1 success, 0 failures, 47s, `build/maestro/den-18-review-repeat/ios`.

The review repeat reused the exact checked-in flow set later referenced by this node, with clear state and debug seed and no exploratory selector or path discovery. This iOS evidence is only for the configured iOS 26.5 simulator, not iOS 16.

## Invalidation

Re-verify if Settings routing, system-back subflows, semantic IDs, or theme selection/process-local runtime preference behavior changes. A platform gesture that remains on Settings or a non-List completion invalidates this record.
