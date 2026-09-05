# iOS host and adapters

<!-- fact-owner: ios-boundary -->
<!-- canonical-signature: ios-boundary-v1 -->

`iosApp` owns SwiftUI lifecycle, URL delivery, Debug-only runtime holder, and the temporary UIKit leading-edge gesture adapter. It creates one shared runtime/controller; product navigation and screens remain common. Deployment and target details are owned by [stack and toolchain](01-stack-toolchain.md); the host builds the shared static framework.

iOS creates `Application Support/HabitLab` before opening Room, excludes the directory from backup, and applies `NSFileProtectionCompleteUntilFirstUserAuthentication`; failure to apply policy aborts bootstrap. BundledSQLite uses `Dispatchers.Default`, because the Kotlin/Native coroutine artifact does not expose IO. Route-only snapshots use `NSUserDefaults` and do not contain screen state.

SwiftUI forwards raw URLs and the edge adapter forwards only a completed back request. The adapter never mirrors a route stack and is removable when Compose/Nav3 edge recognition works in the SwiftUI embedding; [ADR 0001](../../docs/adr/0001-navigation3-ios-edge-adapter.md) owns that exception. Automation tags map to `accessibilityIdentifier`; the iOS bridge is an identity modifier.

Common interfaces are constructor-injected. Foundation, UIKit, Swift, and Objective-C types stay in `iosMain`/`iosApp` and must be mapped to common values at the boundary. See [architecture boundaries](02-architecture-boundaries.md) and the [adapter recipe](09-common-cases.md).

Health-platform capability status and introduction requirements are owned by [libraries and licenses](08-libraries-licenses.md) and the [ADR policy](../../docs/adr/README.md). The configured minimum iOS runtime still needs device/simulator validation; build compatibility is not runtime evidence.
