# Today / Experiment List

**Destination and purpose.** `AppDestination.Today` is the stable post-Launch-Gate List root. It exposes the fixed debug-seed `daily-movement` and `sleep-routine` rows, create, and Settings entry points. `AppDestination.Gallery` was intentionally removed by DEN-33; v2 Gallery snapshots are rejected and cleared rather than restored.

**Admission.** Owner gate: pending; not yet admissible as terminal reusable memory. This route-migration candidate preserves attributable Android and iOS evidence for source revision `0ac8e31c9e582ea09445ecaaf7471613d8c029bf`.

## Fixture, IDs, and paths

Start with [`launch-reference-fixture.yaml`](../../../flows/launch-reference-fixture.yaml): it clears state, launches debug, atomically seeds a completed onboarding protocol, resolves Launch Gate to Today, and waits for both fixed rows. App-owned sequence: `habitlab.experiment-list.screen.root`, either fixed row ID, `habitlab.experiment-list.action.create`, and `habitlab.experiment-list.action.open-settings`. Other rows use the generic closed row ID. The expected completion is the Today/List root.

The fixed rows pass a typed `ExperimentId` to [`open-daily-details.yaml`](../../../flows/open-daily-details.yaml) or [`open-sleep-details.yaml`](../../../flows/open-sleep-details.yaml). Create opens `ExperimentEditor(null)` through [`open-create-editor.yaml`](../../../flows/open-create-editor.yaml); Settings opens `Settings` through [`open-settings.yaml`](../../../flows/open-settings.yaml). The list-return flows restore Today. Today has no app back destination.

## Evidence

Verification date: 2026-09-07. The checked-in `reference-screens.yaml` completed without selector discovery or flow changes.

- Android candidate evidence: source `0ac8e31c9e582ea09445ecaaf7471613d8c029bf`; `emulator-5554`, AVD `FO_Play_API36_1`, API 36; Maestro 2.6.1, JBR 21.0.11. Run `den33-resume-android-20260907-01`: 1/1 passed in 59s, `build/maestro/den33-resume-android-20260907-01/android`.
- iOS candidate evidence: source `0ac8e31c9e582ea09445ecaaf7471613d8c029bf`; iPhone 17 Pro, iOS 26.5, UDID `19C4B36C-E2E9-43C3-BB33-B762FFDA5A08`; Xcode 26.6, Maestro 2.6.1, JBR 21.0.11. Run `den33-resume-ios-20260907-01`: 1/1 passed in 48s, `build/maestro/den33-resume-ios-20260907-01/ios`.

## Invalidation

Re-verify when Today or Launch Gate routing, the completed debug fixture, row-ID mapping, or a listed flow changes. A missing fixed row, Gallery restoration, or non-Today root invalidates this candidate.
