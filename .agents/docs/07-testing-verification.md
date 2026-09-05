# Testing and verification

<!-- fact-owner: testing-policy -->
<!-- canonical-signature: testing-policy-v1 -->

Tests live beside their ownership boundary. Pure build-logic tests live in `buildSrc/src/test`. Portable business, mapper, ViewModel, navigation codec, and Room contract tests live in `shared/src/commonTest`; file-backed target fixtures live in Android device/iOS test source sets. Use fakes at domain/capability boundaries and test behavior/invariants rather than private implementation.

For every change, run the narrowest relevant test while iterating, then the normal owner gate. Documentation uses the documentation gate; shared production uses the shared check (which includes documentation and package boundaries); build logic uses its tests plus documentation. Copy invocations only from the [command catalog](01-stack-toolchain.md#commands).

CI runs the fast documentation/build-logic gate before simulator/emulator jobs. The macOS job uses the pinned [CI toolchain](01-stack-toolchain.md) and runs shared checks plus the Apple Silicon simulator suite. The Android job runs the inherited common suite on one configured emulator.

UI/navigation changes also require the Compose rule's Android/iOS build and scenario checks. Persistence work verifies reopen behavior, invalid data, transaction invariants, observer updates, and schema export. Route work verifies serializers, validator, snapshot version/restore fallback, deep links, back, and caller-scoped results. Platform adapters require target compilation plus native boundary behavior.

Before release, run the affected Android build and iOS framework/Xcode build, then manually exercise any OS behavior not covered by automation. Do not claim minimum-iOS runtime validation without an installed iOS 16 runtime. Existing common tests are a business-logic gate, not full Android/iOS UI parity coverage.
