# Common implementation cases

These are ordering recipes. The linked owner documents define policy.

## Screen

Read [the Compose screen rule](../rules/compose.md) and [presentation policy](03-presentation-navigation.md). Create a feature/screen package with State, ViewModel, Screen, optional UI mapper, and sections. Keep Content stateless, resolve an entry-scoped ViewModel at the host boundary, route typed effects outward, use resources/automation IDs, and verify both targets.

## Route, deep link, back, or restoration

Follow [navigation policy](03-presentation-navigation.md). Update `AppDestination`, the explicit polymorphic serializers module, transition/parent validator, snapshot codec/version when compatibility changes, and matching tests. Register the entry in the single common owner and keep native hosts as event adapters. Validate malformed input and safe-root fallback. Use an [ADR](../../docs/adr/README.md) for framework/stack or incompatible persisted-format policy changes.

## Dialog and result

Follow [dialog policy](03-presentation-navigation.md). Model a dialog route and typed caller/result. Validate the immediate caller, pop and persist before delivery, then send a caller-scoped one-shot result. Never snapshot or store a result in the route or `ViewState`; funnel explicit cancel and system/edge dismiss through the same path.

## Repository or Room slice

Follow [boundaries](02-architecture-boundaries.md) and [offline-first policy](04-data-offline-first.md). Define the domain contract/result plus Interactor and Observer first. Implement the data source, Room entity/DAO, explicit mapper, repository, transaction, and typed failure mapping in data. Expose observations as `Flow`, rethrow cancellation, export schema changes, and keep Room types out of presentation.

## Platform adapter

Follow [architecture boundaries](02-architecture-boundaries.md) and the applicable [Android](05-platform-android.md) or [iOS](06-platform-ios.md) owner. Define a narrow common interface/value contract, constructor-inject its native implementation at bootstrap, and map native events/storage results at the edge. Native types stop at the source-set/host boundary; common code remains the state owner.
