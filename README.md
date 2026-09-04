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

`App()` now boots a shared, scrollable component gallery. It demonstrates the common Material 3 theme,
toolbar, buttons, text field, rows, loading/empty/error blocks, and dialog on both Android and iOS.
Feature and layer Gradle modules are not created until a task specifically justifies them.

## UI automation contract

The gallery exposes a fixed, locale-independent `habitlab.gallery.*` selector namespace through the closed
`AutomationId` enum and `ComponentGalleryAutomationIds` aliases. Every gallery interaction and explicit
loading, empty, and error state has its own direct Compose `testTag`; selectors never contain display text,
user data, or runtime values.

The gallery scaffold and dialog shell are the small platform bridge boundaries. On Android they enable
`testTagsAsResourceId`, so legacy UiAutomator can select tagged nodes with `By.res(...)`, including dialog
actions in their separate window. On iOS, Compose 1.12 maps the same tags to `accessibilityIdentifier` for
XCTest, so the iOS bridge is intentionally an identity modifier. Product composables contain no platform
conditionals and each target node is tagged directly rather than relying on the root selector.

## Shared package boundaries

The shared module keeps `core`, `domain`, `data`, `presentation`, `di`, and `app` as packages rather than Gradle modules. Run `./gradlew :shared:checkArchitectureBoundaries` to verify their common-source dependency directions; it is also part of `:shared:check`.
