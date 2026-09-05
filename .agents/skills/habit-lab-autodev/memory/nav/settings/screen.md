# Candidate evidence: Settings

**Destination and purpose.** `AppDestination.Settings` is entered only from Gallery and presents theme selection plus a toolbar back control. The checked source revision is `f479db59f86b3894c316ca69b251f3a29c3af9ce`; the exact [reference flow contract](../../../../../../ui-tests/maestro/flows/reference-screens.yaml) was verified at that revision. This evidence record was added afterward and does not modify the verified production/flow revision.

**Admission.** Owner gate: pending; not yet admissible as terminal reusable memory. Promotion requires the manager's factual full passing-gate status.

## Fixture, IDs, and paths

Start at the debug-seeded List root and run [`open-settings.yaml`](../../../flows/open-settings.yaml). App-owned sequence is `habitlab.experiment-list.screen.root`, `habitlab.experiment-list.action.open-settings`, `habitlab.settings.screen.root`, `habitlab.settings.action.back`, and the closed theme IDs. The expected Settings completion is its screen root.

The platform return is intentionally limited to the existing [`android-system-back.yaml`](../../../../../../ui-tests/maestro/flows/platform/android-system-back.yaml) or [`ios-edge-back.yaml`](../../../../../../ui-tests/maestro/flows/platform/ios-edge-back.yaml), each ending at List. The iOS flow's leading-edge swipe is the sole coordinate exception; no app control is selected by coordinate. Settings has no typed child argument or dialog result.

## Evidence

Verification date: 2026-09-05. Reviewer: Codex Manager checked JUnit and command/debug/trace output and visually inspected all six initial screenshots. Every initial and repeat directory contains `command.log`, `report.xml`, three screenshots, `debug/maestro.log`, and `debug/commands-(reference-screens).json`; no filename contained `failure`, `error`, or `❌`.

- Android — `emulator-5554`, AVD `FO_Play_API36_1`, API 36; Maestro 2.6.1, JBR 21.0.11. Initial run ID `den-18-initial`: 1/1 success, 0 failures, 72s, `build/maestro/den-18-initial/android`. Repeat run ID `den-18-repeat`: 1/1 success, 0 failures, 73s, `build/maestro/den-18-repeat/android`.
- iOS — iPhone 17 Pro, iOS 26.5, UDID `19C4B36C-E2E9-43C3-BB33-B762FFDA5A08`; Xcode 26.6, Maestro 2.6.1, JBR 21.0.11. Initial run ID `den-18-initial`: 1/1 success, 0 failures, 44s, `build/maestro/den-18-initial/ios`. Repeat run ID `den-18-repeat`: 1/1 success, 0 failures, 44s, `build/maestro/den-18-repeat/ios`.

Repeat reused the exact checked-in flow set later referenced by this candidate, with clear state and debug seed and no exploratory selector or path discovery. This iOS evidence is only for the configured iOS 26.5 simulator, not iOS 16.

## Invalidation

Re-verify if Settings routing, system-back subflows, semantic IDs, or theme persistence behavior changes. A platform gesture that remains on Settings or a non-List completion invalidates this record.
