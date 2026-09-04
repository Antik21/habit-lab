# Habit Lab

Offline-first Kotlin Multiplatform app for personal habit experiments, using Health Connect and HealthKit to explore links between daily habits and well-being.

## Requirements

- JDK 17 or newer (the project is verified with JDK 25).
- Android SDK Platform 37 and Android Build Tools 36.0.0; set `ANDROID_HOME` or configure it in `local.properties` locally. The app still targets SDK 36.
- Xcode 26.4 or newer with an iOS Simulator runtime. The app deploys to iOS 16.0 and newer.

## Build and run

```sh
./gradlew :androidApp:assembleDebug
```

Open `iosApp/iosApp.xcodeproj` in Xcode, choose an iOS Simulator, and run the `iosApp` scheme. The Xcode build phase invokes `:shared:embedAndSignAppleFrameworkForXcode` to compile and embed the shared Compose framework.

For a command-line simulator build:

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -configuration Debug build
```

`App()` boots a shared Experiment List as its root destination. It is backed by the common Room
projection and provides the reference List, Details, Editor, Daily Check-in, and Settings screens
on both Android and iOS. Feature and layer Gradle modules are not created until a task specifically
justifies them.

## Production Navigation 3 shell

The app uses the Compose Multiplatform artifact
`org.jetbrains.androidx.navigation3:navigation3-ui:1.1.1` and one app-owned `NavBackStack` in
`shared.app`. Routes are `@Serializable` keys with only typed IDs, and the host supplies an explicit
`SavedStateConfiguration`/polymorphic serializers module so the stack participates in common saved
state without the Android reflection overload. There is one `NavDisplay`; the dialog is a `DialogSceneStrategy` overlay
and the two-step flow is represented by entries in that same stack.

Each screen collects state and one-shot Orbit side effects from its entry-scoped common AndroidX
ViewModel, handles view effects locally, and forwards typed navigation effects to the host. The host
is the only code that mutates the app-owned Nav3 stack. Koin resolves ViewModels at the navigation
entry composition boundary after Nav3 saved-state and ViewModel-store decorators establish ownership.
Routes carry typed IDs only; an experiment re-reads its current projection through a domain observer,
so `ViewState` and screen data are never serialized into a route.

A versioned common route snapshot persists the final validated stack after every completed navigation
operation through `SharedPreferences` on Android and `NSUserDefaults` on iOS. Android serializes
durable `SharedPreferences.commit()` calls on a background executor; Android Navigation 3 saved state
remains active for Activity recreation. Corrupt, stale, unknown, or structurally invalid snapshots
are discarded in favour of Gallery; completed dialog results are one-shot caller-scoped effects,
never serialized state. The complete policy and release validation matrix are in
[`docs/navigation/den-10-production-navigation-shell.md`](docs/navigation/den-10-production-navigation-shell.md).

Android and iOS host layers only bridge external `habitlab://` URLs into common code and keep the
active stack when opening application settings. The common parser accepts exactly
`habitlab://experiment/daily-movement` or `habitlab://experiment/sleep-routine`; malformed, missing,
or unknown URLs reset safely to the gallery. A handled bridge event is consumed by its exact ID, so a
host remount cannot replay it while a repeated live URL still receives a new ID. Historical
compatibility evidence remains in
[`docs/spikes/den-9-navigation3-kmp.md`](docs/spikes/den-9-navigation3-kmp.md).

The iOS host contains a small, temporary leading-edge gesture adapter because the Compose-provided
edge recognizer was not dispatched in the SwiftUI embedding during simulator automation. It forwards
only a completed back request to the common navigator, keeping the Nav3 stack as the sole source of
truth. The rationale and removal criteria are recorded in
[`docs/adr/0001-navigation3-ios-edge-adapter.md`](docs/adr/0001-navigation3-ios-edge-adapter.md).

The root list opens every persisted `ExperimentSummary`, including locally generated `draft-*` IDs.
It can create a draft, open Settings, and navigate to Details; Details opens the draft editor, the
typed LocalDate daily check-in, and the destructive confirmation dialog. The legacy gallery and
two-step flow packages remain source-compatible historical scaffolding, but are no longer app routes.

## UI automation contract

The gallery and navigation shell expose fixed, locale-independent `habitlab.gallery.*` and
`habitlab.navigation.*` selector namespaces through the closed `AutomationId` enum and its scoped
aliases. Every gallery interaction, navigation action, dialog result, and explicit loading, empty, and
error state has its own direct Compose `testTag`; selectors never contain display text, user data, or
runtime values.

The gallery scaffold and dialog shell are the small platform bridge boundaries. On Android they enable
`testTagsAsResourceId`, so legacy UiAutomator can select tagged nodes with `By.res(...)`, including dialog
actions in their separate window. On iOS, Compose 1.12 maps the same tags to `accessibilityIdentifier` for
XCTest, so the iOS bridge is intentionally an identity modifier. Product composables contain no platform
conditionals and each target node is tagged directly rather than relying on the root selector.

## Shared package boundaries

The shared module keeps `core`, `domain`, `data`, `presentation`, `di`, and `app` as packages rather than Gradle modules. Run `./gradlew :shared:checkArchitectureBoundaries` to verify their common-source dependency directions; it is also part of `:shared:check`.

## Shared business-logic verification

Business-logic tests live in `shared/src/commonTest`, use only portable test dependencies, and run on
both native targets in CI. The macOS job runs `./gradlew :shared:check :shared:iosSimulatorArm64Test`;
the Android job runs the inherited common suite through `:shared:connectedAndroidDeviceTest` on one
emulator. This is deliberately a narrow shared-business-logic gate: it does not add Android/iOS UI or
navigation parity coverage, which remains separate work.

## Reference screen contracts

DEN-12 adds one common Navigation 3 stack with route-only `ExperimentId` and typed local-date
arguments. The route snapshot is version 2; old snapshots are rejected safely and restart at the
Experiment List. Metric picking returns a one-shot typed result only to its editor caller. Delete
confirmation performs the real narrow domain/repository/Room delete path before its result is
delivered; Room's existing cascade removes dependent check-ins.

Settings uses a process-owned, observable common theme preference. It immediately changes
`HabitLabTheme` across active Android and iOS entries, but is intentionally not persisted in DEN-12:
adding DataStore or a platform preference implementation solely for this reference screen would
introduce an unsupported persistence policy. The selected metric is likewise a typed editor UI
choice used to demonstrate caller-scoped dialog results; v1 `Experiment` has no metric field, so
it is not persisted as an experiment attribute.

## Offline-first experiment persistence

DEN-11 adds a shared Room 3.0.2 + BundledSQLite local store for experiments and daily check-ins.
The committed v1 schema, debug seed/reset contract, Android/iOS path ownership, persistence smoke
procedure, release behavior, and v1 limitations are documented in
[`docs/data/den-11-room-offline-first.md`](docs/data/den-11-room-offline-first.md).
