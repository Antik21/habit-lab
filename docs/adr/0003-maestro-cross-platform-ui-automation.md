# ADR 0003: Maestro cross-platform UI automation

- Status: Accepted
- Owner: Habit Lab maintainers
- Date: 2026-09-05
- Supersedes: none
- Superseded by: none

## Context

Habit Lab renders the same production Compose screens on Android and iOS, but shared unit tests do
not establish that native accessibility bridges expose the same controls or that the typed dialog
results reach their callers at runtime. A durable black-box convention is needed for the five
reference screens (Experiment List, Details, Editor, Daily Check-In, and Settings) and the Metric
Picker and Confirm Delete dialogs.

The repository already exposes closed, locale-independent automation IDs from common UI. Android
maps Compose test tags to resource IDs and iOS exposes the same identities through its accessibility
bridge. The automation layer must consume that contract without taking ownership of navigation,
domain state, or platform adapters.

## Decision

Use the external Maestro CLI 2.6.1 with application ID `com.denis.habitlab`. The repository owns one
common flow at `ui-tests/maestro/flows/reference-screens.yaml`, shared configuration at
`ui-tests/maestro/config.yaml`, and a runner invoked as
`./ui-tests/maestro/run.sh android|ios <device-id> [run-id]`. Every invocation names an explicit
Android emulator or iOS simulator; physical devices are outside this test contour.

App-owned selectors use only closed automation IDs. Localized text, coordinates, and values derived
from users or runtime data are not selector contracts. Android system back and the native iOS
leading-edge gesture may use platform subflows under `ui-tests/maestro/flows/platform/`. Percentage
coordinates are allowed only inside `ios-edge-back.yaml` to express that native system gesture; they
are not allowed for app-owned controls.

The Editor's metric button retains its action ID. A separate semantic state marker exposes exactly
one closed ID for unset, daily energy, or sleep quality so the common flow can verify the typed Metric
Picker result without inspecting localized labels.

Evidence is isolated by run and platform under `build/maestro/<run-id>/<platform>/`. Each platform
directory contains a command log, JUnit report, screenshots, and Maestro debug output. Generated
evidence is ignored build output and is not committed.

The execution baseline is simulator-only. The current local environment is macOS 26.6.2 arm64,
Xcode 26.6 with an iOS 26.5 simulator, an Android API 36 emulator, and JBR 21 for Maestro. Maestro
requires JDK 17 or newer and does not require Python; local Python 3.9.6 is incidental. Repository CI
continues to use Xcode 26.4.1 and Temurin 17 and does not yet run this Maestro contour.

## Alternatives

- Separate Android and iOS product flows were rejected because they would duplicate the common UI
  contract and could silently diverge.
- Localized labels or app-control coordinates were rejected because locale and layout changes would
  make them unstable without changing product behavior.
- Gradle-managed Maestro dependencies were rejected because Maestro is an external CLI, not code
  linked into the KMP application.
- Treating Windows as cross-platform proof was rejected because Windows has no local iOS simulator;
  iOS execution requires macOS infrastructure.

## Consequences

One scenario can detect cross-platform gaps in identity exposure and typed navigation results while
keeping common UI and Navigation 3 as the owners. Explicit device selection makes evidence
attributable and prevents accidental execution against a different target.

The runner, flows, and ignored output convention require maintenance alongside screen automation
IDs. iOS remains dependent on an available Xcode simulator runtime. Testing iOS 26.5 does not prove
the declared minimum iOS 16 runtime, which remains pending until that runtime is installed.

The current Xcode scheme reports an empty Supported Platforms list when `xcodebuild` receives an
explicit simulator destination ID. The runner therefore fresh-builds the simulator product with
`-sdk iphonesimulator` and the host architecture, then validates, boots, installs, and runs only on
the requested `simctl`/Maestro UDID. This preserved explicit runtime targeting for the current proof,
but the scheme limitation must be resolved before a future build adapter promises a
destination-bound `xcodebuild` invocation.

The iOS build also links `libicu.icudtl_dat.o`, built for iOS Simulator 18.5, into an application
targeting iOS 16.0. Xcode emits a linker warning rather than failing the iOS 26.5 build, so the
current Maestro scenario remains valid on that simulator. Combined with the unavailable iOS 16
runtime, however, this is a compatibility blocker for claiming minimum-iOS 16 runtime or linked
artifact compatibility. It must be investigated and resolved before the runtime-adapter/release
gate makes that claim; DEN-15 records the observation without changing the dependency.

## Migration/rollback

Add the runner, configuration, common flow, and two narrowly scoped platform subflows without changing
application persistence or navigation. Removing these repository files rolls back the automation
contour; application binaries and stored data remain compatible. A Maestro upgrade requires updating
the toolchain pin and re-running both platform scenarios before acceptance.

## Verification

Required verification is:

- `./gradlew :shared:check`, Android assembly, and the iOS simulator Xcode build;
- `./ui-tests/maestro/run.sh android <device-id> [run-id]` on the API 36 emulator;
- `./ui-tests/maestro/run.sh ios <device-id> [run-id]` on the iOS 26.5 simulator;
- review of `command.log`, `report.xml`, screenshots, and debug output in each platform directory under
  `build/maestro/<run-id>/`.

Canonical final run `den-15-evidence-guard` used freshly built current DEN-15 working-tree sources
based on HEAD `e0434eb3719958586f8769c81f0fc554001fc84c` plus the task changes. Android passed 1/1 flows
with zero failures in 40 seconds on `emulator-5554`, AVD `FO_Play_API36_1`, API 36. iOS passed 1/1
flows with zero failures in 24 seconds on iPhone 17 Pro/iOS 26.5 simulator
`19C4B36C-E2E9-43C3-BB33-B762FFDA5A08`.

The common scenario proved all five production screens, stable-ID list selection and input, typed
navigation, the selected-metric state ID, toolbar back, Android system back, and the iOS edge swipe
using the sole percentage-coordinate exception. It also exercised Confirm Delete cancel and confirm
paths and ended with a successful stable-ID `assertVisible` on Experiment List.

The ignored local directories `build/maestro/den-15-evidence-guard/android/` and
`build/maestro/den-15-evidence-guard/ios/` each contain `command.log`, a zero-failure `report.xml`,
three screenshots, non-empty `debug/maestro.log`, and the common-flow command JSON. The runner now
validates those evidence postconditions after execution. Neither directory contains a filename with
`failure`, `error`, or `❌`, and the Maestro results contain no stale test failures. The iOS command
log is not warning-free: its linker reports that `libicu.icudtl_dat.o` was built for iOS Simulator
18.5 while the application target is 16.0. This is simulator-only local evidence: Maestro is still
absent from repository CI, and minimum-iOS 16 runtime and linked-artifact compatibility proof remain
pending.

## Related docs

- [Stack and toolchain](../../.agents/docs/01-stack-toolchain.md)
- [Testing and verification](../../.agents/docs/07-testing-verification.md)
- [Libraries, versions, and licenses](../../.agents/docs/08-libraries-licenses.md)
- [Compose screen rules](../../.agents/rules/compose.md)
- [Temporary iOS leading-edge back adapter](0001-navigation3-ios-edge-adapter.md)
