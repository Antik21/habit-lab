# Stack and toolchain

<!-- fact-owner: toolchain -->
<!-- canonical-signature: toolchain-v1 -->

The wrapper uses Gradle 9.3.1. Project plugins use AGP 9.1.1 and Kotlin/serialization/Compose compiler 2.4.10; Compose Multiplatform is 1.12.0. Android compiles with SDK 37, has minimum SDK 33 and target SDK 36, and emits JVM 17 bytecode.

Kotlin targets are Android, iOS arm64 device, and iOS arm64 simulator. `iosX64` is configured only on Intel macOS. The Xcode project deploys to iOS 16.0. CI selects Xcode 26.4.1 and Temurin JDK 17. A contributor's installed Xcode or JDK may differ; CI does not verify JDK 25.

`./gradlew --version` reports Gradle's embedded Kotlin (currently 2.2.21), not the project's Kotlin plugin version. Read `gradle/libs.versions.toml` for project versions.

Cross-platform UI automation uses the external Maestro CLI pinned to 2.6.1; it requires JDK 17 or newer and is not a Gradle dependency. The AutoDev frozen-checklist gate requires Python 3.9 or newer and uses only the standard library; Maestro itself does not require Python. The recorded local environment is macOS 26.6.2 arm64 with Xcode 26.6/iOS 26.5, an Android API 36 emulator, JBR 21 for Maestro, and Python 3.9.6. These local facts do not replace the CI pins above.

## Commands

```sh
./gradlew checkDocumentation
./gradlew :buildSrc:test checkDocumentation --stacktrace
./gradlew :shared:check
./gradlew :androidApp:assembleDebug
./gradlew :shared:iosSimulatorArm64Test
./gradlew :shared:iosX64Test
./gradlew :shared:connectedAndroidDeviceTest
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build
./ui-tests/maestro/run.sh android <device-id> [run-id]
./ui-tests/maestro/run.sh ios <device-id> [run-id]
./gradlew checkMaestroShell
./ui-tests/maestro/tests/xcode-preflight-test.sh
python3 .agents/skills/habit-lab-autodev/scripts/autodev_gate.py --help
```

`:shared:check` includes architecture and documentation checks, plus the Maestro shell contract check on non-Windows hosts when Bash is available. Native Windows and hosts without Bash skip that shell-only task and do not establish its coverage. Android device tests require one API 33+ device; CI uses an API 36 Google APIs x86_64 emulator. Run `:shared:iosSimulatorArm64Test` on Apple Silicon and `:shared:iosX64Test` on Intel macOS, where that target is configured. See [testing and verification](07-testing-verification.md) before choosing a subset.

The Maestro runner owns CLI invocation and targets `ui-tests/maestro/flows/reference-screens.yaml`; `ui-tests/maestro/config.yaml` is its shared configuration. Platform-only system gestures live under `ui-tests/maestro/flows/platform/`. The runner and flows are repository files, while the CLI remains external.

Dependencies are centralized in `gradle/libs.versions.toml`; policy for adding them is owned by [libraries and licenses](08-libraries-licenses.md).
