# Presentation and navigation

<!-- fact-owner: presentation-navigation -->
<!-- canonical-signature: presentation-navigation-v1 -->

`Navigation3AppHost` owns one app-wide Navigation 3 `NavBackStack` and is the only mutator. The current production `AppDestination` set is `Gallery` (the stable wire key rendering Experiment List), `Experiment`, `ExperimentEditor`, `DailyCheckIn`, `Settings`, `MetricPicker`, and `ConfirmDelete`. The legacy `presentation.gallery` and `presentation.navigation.flow` sources are compatibility scaffolding, not product routes.

Entries install saved-state and ViewModel-store decorators, then resolve entry-scoped common ViewModels at the Koin composition boundary. Screens collect Orbit state/effects, handle local `ViewEffect`, and forward typed `NavigationEffect`. Entry-owned UI navigation actions are accepted only while their entry is `RESUMED`; system/edge back and structural recovery effects such as `PopToRoot` intentionally bypass that gate. The host still validates route structure before mutation. Routes contain only `ExperimentId`, nullable editor IDs, or validated `CheckInRouteDate`.

## Back, links, and restoration

The common host handles Android system back and iOS adapter requests. Native hosts only forward events. External URLs accept exactly `habitlab://experiment/daily-movement` and `habitlab://experiment/sleep-routine`; invalid input safely resets to `Gallery`. An event is consumed by its exact ID after handling.

<!-- fact-owner: route-restoration -->
<!-- canonical-signature: route-restoration-v1 -->

The persisted route snapshot format is version 2. `AppDestination`, its serializers module, structural validator, snapshot codec/store, typed `CheckInRouteDate`, and matching tests change together. The final validated stack is saved after complete mutations, never during a remove/add intermediate state. Invalid JSON, versions, IDs/dates, length, parents, or dialog callers are cleared and fall back to `Gallery`. `ViewState`, domain/entity data, projections, and delivered results are never serialized. Incompatible format changes require an ADR and version bump. Platform I/O mechanics belong to the [Android](05-platform-android.md) and [iOS](06-platform-ios.md) owners.

## Dialog results

`MetricPicker` and `ConfirmDelete` are Nav3 dialog scenes. The host verifies the immediate typed caller, pops the dialog, queues a caller-scoped one-shot typed result, then awaits persistence of the resulting stack; recomposition can deliver the queued result to that live caller. Results do not enter route fields, `ViewState`, or snapshots. System/edge dismissal follows the same cancellation path. Delete confirmation completes the domain/Room delete before confirmed resolution is queued.

New screens follow [the Compose rule](../rules/compose.md). Use [common recipes](09-common-cases.md) for change order, not as a policy substitute.
