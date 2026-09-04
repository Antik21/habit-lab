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

`App()` boots a shared, scrollable component gallery as its root destination. It demonstrates the
common Material 3 theme, toolbar, buttons, text field, rows, loading/empty/error blocks, and a
minimal Navigation 3 shell on both Android and iOS. Feature and layer Gradle modules are not created
until a task specifically justifies them.

## Navigation 3 compatibility spike

The app uses the Compose Multiplatform artifact
`org.jetbrains.androidx.navigation3:navigation3-ui:1.1.1` and one app-owned `NavBackStack` in
`shared.app`. Routes are `@Serializable` keys with only typed IDs, and the host supplies an explicit
`SavedStateConfiguration`/polymorphic serializers module so the stack participates in common saved
state without the Android reflection overload. There is one `NavDisplay`; the dialog is a `DialogSceneStrategy` overlay
and the two-step flow is represented by entries in that same stack.

This retained code is a minimal compatibility/foundation harness for future DEN-10 work, rather than a
complete production navigation graph. The spike exercised Android 16/API 36 and iOS 26.5 simulator
flows for host lifecycle changes, deep links, settings round trips, dialogs, and back navigation.
Android Activity recreation restores the stack; iOS preserves it across orientation changes but a
terminated process currently starts at the gallery. The project still builds with an iOS 16.0
deployment target; an iOS 16 runtime was not available for this run. These two iOS limitations and
their migration path are recorded in
[`docs/adr/0002-navigation3-ios-restoration-runtime.md`](docs/adr/0002-navigation3-ios-restoration-runtime.md).

Android and iOS host layers only bridge external `habitlab://` URLs into common code and keep the
active stack when opening application settings. The common parser accepts exactly
`habitlab://experiment/daily-movement` or `habitlab://experiment/sleep-routine`; malformed, missing,
or unknown URLs reset safely to the gallery. The detailed QA evidence template and commands are in
[`docs/spikes/den-9-navigation3-kmp.md`](docs/spikes/den-9-navigation3-kmp.md).

The iOS host contains a small, temporary leading-edge gesture adapter because the Compose-provided
edge recognizer was not dispatched in the SwiftUI embedding during simulator automation. It forwards
only a completed back request to the common navigator, keeping the Nav3 stack as the sole source of
truth. The rationale and removal criteria are recorded in
[`docs/adr/0001-navigation3-ios-edge-adapter.md`](docs/adr/0001-navigation3-ios-edge-adapter.md).

The gallery primary action remains the legacy `Open dialog` control and retains its existing gallery-dialog
selectors. Gallery rows open experiments, while the secondary action starts the nested navigation flow.

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
