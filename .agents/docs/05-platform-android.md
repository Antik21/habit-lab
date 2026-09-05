# Android host and adapters

<!-- fact-owner: android-boundary -->
<!-- canonical-signature: android-boundary-v1 -->

`androidApp` owns `Application`, `Activity`, manifest, debug flag, external Intent ingestion, Android SDK configuration, and the application context used to create shared capabilities. It initializes one `HabitLabRuntime` and renders the common `App`; product navigation and UI remain common.

Android Room receives an application-owned database path, BundledSQLite, and `Dispatchers.IO`. The manifest sets `android:allowBackup="false"`. The route-only snapshot capability uses private `SharedPreferences`; writes use serialized `commit()` calls on a background executor and await completion. This store does not persist screen state. Android Navigation 3 saved state separately covers Activity recreation.

`ACTION_VIEW` URLs are forwarded as raw strings to the common event bridge. The host's handled-intent marker prevents a stale launch Intent from replacing a restored stack; it does not parse routes. Application settings are exposed through the common `AppSettingsCapability`. Compose automation enables `testTagsAsResourceId` only in the Android bridge.

Adapters implement common interfaces through constructor injection. Android framework types stay in `androidMain`/`androidApp`; do not leak `Context`, `Intent`, Room builders, or Android lifecycle types into domain, data contracts, or presentation. See [architecture boundaries](02-architecture-boundaries.md) and the [adapter recipe](09-common-cases.md).

Health-platform capability status and introduction requirements are owned by [libraries and licenses](08-libraries-licenses.md) and the [ADR policy](../../docs/adr/README.md).
