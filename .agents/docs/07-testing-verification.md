# Testing and verification

<!-- fact-owner: testing-policy -->
<!-- canonical-signature: testing-policy-v1 -->

Tests live beside their ownership boundary. Pure build-logic tests live in `buildSrc/src/test`. Portable business, mapper, ViewModel, navigation codec, and Room contract tests live in `shared/src/commonTest`; file-backed target fixtures live in Android device/iOS test source sets. Use fakes at domain/capability boundaries and test behavior/invariants rather than private implementation.

For every change, run the narrowest relevant test while iterating, then the normal owner gate. Documentation uses the documentation gate; shared production uses the shared check (which includes documentation and package boundaries); build logic uses its tests plus documentation. Copy invocations only from the [command catalog](01-stack-toolchain.md#commands).

CI runs the fast documentation/build-logic gate before simulator/emulator jobs. The macOS job uses the pinned [CI toolchain](01-stack-toolchain.md) and runs shared checks plus the Apple Silicon simulator suite. The Android job runs the inherited common suite on one configured emulator.

UI/navigation changes also require the Compose rule's Android/iOS build and scenario checks. Route work verifies the current [route/restoration contract](03-presentation-navigation.md), including its serializers, version 2 snapshot, typed `CheckInRouteDate`, invalid-stack safe fallback, back/deep links, and caller-scoped results. Android adapter persistence behavior is verified against its [platform owner](05-platform-android.md), without duplicating those I/O rules here. Persistence work verifies reopen behavior, invalid data, transaction invariants, observer updates, and schema export. Other platform adapters require target compilation plus native boundary behavior.

The simulator-only cross-platform UI gate runs the same `ui-tests/maestro/flows/reference-screens.yaml` against explicit Android emulator and iOS simulator IDs through the exact [runner commands](01-stack-toolchain.md#commands). App-owned interaction and assertions use stable automation IDs only, never localized text, runtime/user values, or coordinates. Platform subflows are limited to system back and edge gestures; percentage coordinates are permitted only for the native iOS edge gesture in `ui-tests/maestro/flows/platform/ios-edge-back.yaml`. Each run writes separate platform evidence under `build/maestro/<run-id>/<platform>/`: `command.log`, `report.xml`, screenshots, and debug output. These artifacts are ignored build output, not source.

The repository CI does not yet install or run Maestro. Windows can exercise Android through an available emulator, but without a macOS runner it cannot establish iOS parity. The iOS 26.5 simulator target does not prove behavior on the minimum supported iOS 16 runtime.

Before release, run the affected Android build and iOS framework/Xcode build, then manually exercise any OS behavior not covered by automation. Do not claim minimum-iOS runtime validation without the configured minimum runtime installed. Existing common tests are a business-logic gate, not full Android/iOS UI parity coverage.
