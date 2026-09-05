# Habit Lab

Habit Lab is a Kotlin Multiplatform app for personal habit experiments. The current product is local-first: shared Compose screens run on Android and iOS and persist experiments and daily check-ins in a shared Room database.

## Prerequisites

- Install the pinned JDK, Android SDK/Build Tools, Xcode, and an iOS Simulator runtime from the [toolchain owner](.agents/docs/01-stack-toolchain.md).
- Set `ANDROID_HOME` or configure the SDK path in `local.properties`.

## Build and run

For Android, run the debug application build from the exact [command catalog](.agents/docs/01-stack-toolchain.md#commands), then launch it from Android Studio or install the generated debug APK.

Open `iosApp/iosApp.xcodeproj`, choose an iOS Simulator, and run the `iosApp` scheme. Its build phase invokes `:shared:embedAndSignAppleFrameworkForXcode`.

For a terminal-only iOS build and the normal shared verification path, use the exact [command catalog](.agents/docs/01-stack-toolchain.md#commands). Platform test selection and CI coverage are in [testing and verification](.agents/docs/07-testing-verification.md).

## Documentation map

- [Agent request router](AGENTS.md)
- [Toolchain and exact commands](.agents/docs/01-stack-toolchain.md)
- [Architecture boundaries](.agents/docs/02-architecture-boundaries.md)
- [Presentation and navigation](.agents/docs/03-presentation-navigation.md)
- [Data and offline-first policy](.agents/docs/04-data-offline-first.md)
- [Android](.agents/docs/05-platform-android.md) and [iOS](.agents/docs/06-platform-ios.md) host policies
- [Libraries and licenses](.agents/docs/08-libraries-licenses.md)
- [Common implementation recipes](.agents/docs/09-common-cases.md)
- [ADR policy and index](docs/adr/README.md)

For current versus planned capability status, see [libraries and licenses](.agents/docs/08-libraries-licenses.md).
