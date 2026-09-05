# Presentation and navigation

<!-- fact-owner: presentation-navigation -->
<!-- canonical-signature: presentation-navigation-v1 -->

`Navigation3AppHost` owns one app-wide Navigation 3 `NavBackStack` and is the only mutator. The current production `AppDestination` set is `Gallery` (the stable wire key rendering Experiment List), `Experiment`, `ExperimentEditor`, `DailyCheckIn`, `Settings`, `MetricPicker`, and `ConfirmDelete`. The legacy `presentation.gallery` and `presentation.navigation.flow` sources are compatibility scaffolding, not product routes.

Entries install saved-state and ViewModel-store decorators, then resolve entry-scoped common ViewModels at the Koin composition boundary. Screens collect Orbit state/effects, handle local `ViewEffect`, and forward typed `NavigationEffect`; the host validates lifecycle and structure before mutation. Routes contain only `ExperimentId`, nullable editor IDs, or validated `CheckInRouteDate`.

## Back, links, and restoration

The common host handles Android system back and iOS adapter requests. Native hosts only forward events. External URLs accept exactly `habitlab://experiment/daily-movement` and `habitlab://experiment/sleep-routine`; invalid input safely resets to `Gallery`. An event is consumed by its exact ID after handling.

<!-- fact-owner: route-restoration -->
<!-- canonical-signature: route-restoration-v1 -->

The persisted route snapshot format is version 2. `AppDestination`, its serializers module, structural validator, snapshot codec/store, and matching tests change together. The final validated stack is saved after complete mutations, never during a remove/add intermediate state. Invalid JSON, versions, IDs/dates, length, parents, or dialog callers are cleared and fall back to `Gallery`. `ViewState`, domain/entity data, projections, and delivered results are never serialized. Incompatible format changes require an ADR and version bump.

## Dialog results

`MetricPicker` and `ConfirmDelete` are Nav3 dialog scenes. The host verifies the immediate typed caller, pops the dialog, persists the resulting stack, and only then delivers a caller-scoped one-shot typed result. Results do not enter route fields, `ViewState`, or snapshots. System/edge dismissal follows the same cancellation path. Delete confirmation completes the domain/Room delete before confirmation delivery.

New screens follow [the Compose rule](../rules/compose.md). Use [common recipes](09-common-cases.md) for change order, not as a policy substitute.
