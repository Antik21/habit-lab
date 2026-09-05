# Candidate evidence: Experiment List

**Destination and purpose.** `AppDestination.Gallery` is the stable `Gallery` wire key and safe List root. It exposes the fixed debug-seed `daily-movement` and `sleep-routine` rows, create, and Settings entry points. The checked source revision is `f479db59f86b3894c316ca69b251f3a29c3af9ce`; the exact [reference flow contract](../../../../../../ui-tests/maestro/flows/reference-screens.yaml) was verified at that revision. This evidence record was added afterward and does not modify the verified production/flow revision.

**Admission.** Owner gate: pending; not yet admissible as terminal reusable memory. Promotion requires the manager's factual full passing-gate status.

## Fixture, IDs, and paths

Start with [`launch-reference-fixture.yaml`](../../../flows/launch-reference-fixture.yaml): it clears state, launches debug, and waits for both fixed rows. App-owned sequence: `habitlab.experiment-list.screen.root`, `habitlab.experiment-list.row.daily-movement` or `habitlab.experiment-list.row.sleep-routine`, `habitlab.experiment-list.action.create`, and `habitlab.experiment-list.action.open-settings`. Other rows use the generic closed row ID. The expected completion is the List root.

The fixed row IDs pass a typed `ExperimentId` to [`open-daily-details.yaml`](../../../flows/open-daily-details.yaml) or [`open-sleep-details.yaml`](../../../flows/open-sleep-details.yaml). Create opens `ExperimentEditor(null)` through [`open-create-editor.yaml`](../../../flows/open-create-editor.yaml); Settings opens `Settings` through [`open-settings.yaml`](../../../flows/open-settings.yaml). [`back-to-list-from-details.yaml`](../../../flows/back-to-list-from-details.yaml) and [`confirm-delete-to-list.yaml`](../../../flows/confirm-delete-to-list.yaml) restore this root. Gallery has no app back destination.

## Evidence

Verification date: 2026-09-05. Reviewer: Codex Manager, who checked JUnit and command/debug/trace output and visually inspected all six initial screenshots. Every initial and repeat directory contains `command.log`, `report.xml`, three screenshots, `debug/maestro.log`, and `debug/commands-(reference-screens).json`; the reviewer found no filename containing `failure`, `error`, or `❌`.

- Android: `emulator-5554`, AVD `FO_Play_API36_1`, API 36; Maestro 2.6.1 and JBR 21.0.11. Initial run ID `den-18-initial`: 1/1 success, 0 failures, 72s, `build/maestro/den-18-initial/android`. Repeat run ID `den-18-repeat`: 1/1 success, 0 failures, 73s, `build/maestro/den-18-repeat/android`.
- iOS: iPhone 17 Pro, iOS 26.5, UDID `19C4B36C-E2E9-43C3-BB33-B762FFDA5A08`; Xcode 26.6, Maestro 2.6.1, JBR 21.0.11. Initial run ID `den-18-initial`: 1/1 success, 0 failures, 44s, `build/maestro/den-18-initial/ios`. Repeat run ID `den-18-repeat`: 1/1 success, 0 failures, 44s, `build/maestro/den-18-repeat/ios`.

The repeat reused the exact checked-in flow set later referenced by this candidate, with clear state and debug seed and without exploratory selector or path discovery. The iOS result covers this configured simulator, not iOS 16.

## Invalidation

Re-verify when Gallery routing, debug seed, row-ID mapping, or a listed flow changes. A missing fixed row or a non-root completion invalidates this record.
