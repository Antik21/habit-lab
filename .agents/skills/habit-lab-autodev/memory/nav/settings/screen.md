# Settings

**Destination and purpose.** `AppDestination.Settings` is entered from the Experiment List at `AppDestination.Today` and presents theme selection plus a toolbar back control. The original screen evidence remains at revision `8452c8d08152b6d558fc31a08bd4d0223846940c`; DEN-33 reverified the same flow from the Today root at `0ac8e31c9e582ea09445ecaaf7471613d8c029bf`.

**Admission.** Owner gate: pending; not yet admissible as terminal reusable memory. This candidate covers the Today-root route migration; the prior Gallery-root evidence remains historical only.

## Fixture, IDs, and paths

Start at the debug-seeded List root and run [`open-settings.yaml`](../../../flows/open-settings.yaml). App-owned sequence is `habitlab.experiment-list.screen.root`, `habitlab.experiment-list.action.open-settings`, `habitlab.settings.screen.root`, `habitlab.settings.action.back`, `habitlab.settings.theme.system`, `habitlab.settings.theme.light`, and `habitlab.settings.theme.dark`. The expected open-flow completion is the Settings root; its toolbar back is the app-owned `habitlab.settings.action.back` path.

Separately, the platform return is limited to [`android-system-back.yaml`](../../../../../../ui-tests/maestro/flows/platform/android-system-back.yaml) or [`ios-edge-back.yaml`](../../../../../../ui-tests/maestro/flows/platform/ios-edge-back.yaml), each starting at Settings and completing at List. The iOS flow's leading-edge swipe is the sole coordinate exception; no app control is selected by coordinate. Settings has no typed child argument or dialog result.

## Evidence

Historical Gallery admission was verified at `8452c8d08152b6d558fc31a08bd4d0223846940c`; its retained evidence remains in Git history. Today-root verification date: 2026-09-07.

- Android Today-root candidate: source `0ac8e31c9e582ea09445ecaaf7471613d8c029bf`; `emulator-5554`, AVD `FO_Play_API36_1`, API 36; Maestro 2.6.1, JBR 21.0.11. Run `den33-resume-android-20260907-01`: 1/1 passed in 59s, `build/maestro/den33-resume-android-20260907-01/android`.
- iOS Today-root candidate: source `0ac8e31c9e582ea09445ecaaf7471613d8c029bf`; iPhone 17 Pro, iOS 26.5, UDID `19C4B36C-E2E9-43C3-BB33-B762FFDA5A08`; Xcode 26.6, Maestro 2.6.1, JBR 21.0.11. Run `den33-resume-ios-20260907-01`: 1/1 passed in 48s, `build/maestro/den33-resume-ios-20260907-01/ios`.

## Invalidation

Re-verify if Settings routing, system-back subflows, semantic IDs, or theme selection/process-local runtime preference behavior changes. A platform gesture that remains on Settings or a non-List completion invalidates this record.
