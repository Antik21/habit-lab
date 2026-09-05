# Experiment List

**Destination and purpose.** `AppDestination.Gallery` is the stable `Gallery` wire key and safe List root. It exposes the fixed debug-seed `daily-movement` and `sleep-routine` rows, create, and Settings entry points. The checked source revision is `8452c8d08152b6d558fc31a08bd4d0223846940c`; the exact [reference flow contract](../../../../../../ui-tests/maestro/flows/reference-screens.yaml) was verified at that revision. This docs-only successor does not modify the verified source or flows.

**Admission.** Terminal success: confirmed. Full owner gate: passed.

## Fixture, IDs, and paths

Start with [`launch-reference-fixture.yaml`](../../../flows/launch-reference-fixture.yaml): it clears state, launches debug, and waits for both fixed rows. App-owned sequence: `habitlab.experiment-list.screen.root`, `habitlab.experiment-list.row.daily-movement` or `habitlab.experiment-list.row.sleep-routine`, `habitlab.experiment-list.action.create`, and `habitlab.experiment-list.action.open-settings`. Other rows use the generic closed row ID. The expected completion is the List root.

The fixed row IDs pass a typed `ExperimentId` to [`open-daily-details.yaml`](../../../flows/open-daily-details.yaml) or [`open-sleep-details.yaml`](../../../flows/open-sleep-details.yaml). Create opens `ExperimentEditor(null)` through [`open-create-editor.yaml`](../../../flows/open-create-editor.yaml); Settings opens `Settings` through [`open-settings.yaml`](../../../flows/open-settings.yaml). [`back-to-list-from-details.yaml`](../../../flows/back-to-list-from-details.yaml) and [`confirm-delete-to-list.yaml`](../../../flows/confirm-delete-to-list.yaml) restore this root. Gallery has no app back destination.

## Evidence

Verification date: 2026-09-05. Independent reviewer: Codex; final verdict clean. Codex Manager supplied and checked gate results and visually inspected all 12 review screenshots; the independent reviewer also reported them sound. The full owner gate passed: `:buildSrc:test checkDocumentation :shared:check :androidApp:assembleDebug`, 58 connected-Android device tests, iOS simulator common tests, a preflight-wrapped native Xcode build, and `checkMaestroShell` for 15 subflows. Every review directory contains `command.log`, `report.xml`, three screenshots, `debug/maestro.log`, and `debug/commands-(reference-screens).json`; no filename contained `failure`, `error`, or `❌`.

- Android terminal-success, passing-gate evidence: source revision `8452c8d08152b6d558fc31a08bd4d0223846940c`; `emulator-5554`, AVD `FO_Play_API36_1`, API 36; Maestro 2.6.1 and JBR 21.0.11. Initial run ID `den-18-review-initial`: 1/1 success, 0 failures, 73s, `build/maestro/den-18-review-initial/android`. Repeat run ID `den-18-review-repeat`: 1/1 success, 0 failures, 73s, `build/maestro/den-18-review-repeat/android`.
- iOS terminal-success, passing-gate evidence: source revision `8452c8d08152b6d558fc31a08bd4d0223846940c`; iPhone 17 Pro, iOS 26.5, UDID `19C4B36C-E2E9-43C3-BB33-B762FFDA5A08`; Xcode 26.6, Maestro 2.6.1, JBR 21.0.11. Initial run ID `den-18-review-initial`: 1/1 success, 0 failures, 47s, `build/maestro/den-18-review-initial/ios`. Repeat run ID `den-18-review-repeat`: 1/1 success, 0 failures, 47s, `build/maestro/den-18-review-repeat/ios`.

The review repeat reused the exact checked-in flow set later referenced by this node, with clear state and debug seed and without exploratory selector or path discovery. The iOS result covers this configured simulator, not iOS 16.

## Invalidation

Re-verify when Gallery routing, debug seed, row-ID mapping, or a listed flow changes. A missing fixed row or a non-root completion invalidates this record.
