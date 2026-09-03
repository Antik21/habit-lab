# Habit Lab

Offline-first Kotlin Multiplatform app for personal habit experiments, using Health Connect and HealthKit to explore links between daily habits and well-being.

## Requirements

- JDK 17 or newer (the project is verified with JDK 25).
- Android SDK Platform 36 and Android Build Tools 36.0.0; set `ANDROID_HOME` or configure it in `local.properties` locally.
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

The initial screen deliberately contains only the shared `App()` bootstrap UI. Feature and layer Gradle modules are not created until a task specifically justifies them.
