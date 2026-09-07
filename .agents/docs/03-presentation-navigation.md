# Presentation and navigation

<!-- fact-owner: presentation-navigation -->
<!-- canonical-signature: presentation-navigation-v1 -->

`Navigation3AppHost` owns one app-wide Navigation 3 `NavBackStack` and is the only mutator. Cold launch always constructs a non-saveable zero-argument `LaunchGate` stack, then its durable decision replaces that entry with `Welcome`, a canonical `Welcome` + typed `OnboardingCheckpoint(OnboardingStep)` path, or `Today`. `Today` renders the current Experiment List until DEN-41. `Experiment`, `ExperimentEditor`, `DailyCheckIn`, `Settings`, `MetricPicker`, and `ConfirmDelete` remain Today descendants. Legacy `presentation.gallery` and `presentation.navigation.flow` are compatibility scaffolding, never product routes.

Entries install saved-state and ViewModel-store decorators, then resolve entry-scoped common ViewModels at the Koin composition boundary. Screens collect Orbit state/effects, handle local `ViewEffect`, and forward typed `NavigationEffect`. Entry-owned UI navigation actions are accepted only while their entry is `RESUMED`; system/edge back and structural recovery effects such as `PopToRoot` intentionally bypass that gate. The host still validates route structure before mutation. Routes contain only `ExperimentId`, nullable editor IDs, or validated `CheckInRouteDate`.

## Back, links, and restoration

The common host handles Android system back and iOS adapter requests. Native hosts only forward events. `Welcome` and `Today` are non-poppable roots; a canonical onboarding path pops to its preceding reachable checkpoint. Every navigator mutation uses one common transaction. Ordinary post-Today routes and one-shot results stage a best-effort snapshot write or clear before publishing visible Nav3/result state; snapshot I/O cannot block that visible mutation. Gate and Today-admission staging instead require durable snapshot confirmation before publishing. After every incomplete gate decision, an app-owned monitor observes the atomic snapshot and moves to Today only after it proves durable completion; it remains alive if SETUP is popped. External URLs accept exactly `habitlab://experiment/daily-movement` and `habitlab://experiment/sleep-routine`; links received before Today eligibility, including during LaunchGate and incomplete onboarding, stay in FIFO custody. The serialized Today admission persists the initial Today route and stages the complete FIFO before committing its visible Nav3 stack. A cold-admission failure retains LaunchGate for its Failed/Retry state; only a post-onboarding completion admission makes one bounded retry with the full FIFO. Invalid input safely resets to the current canonical root. An event is consumed by its exact ID after handling.

<!-- fact-owner: route-restoration -->
<!-- canonical-signature: route-restoration-v1 -->

The persisted route snapshot format is version 3. `AppDestination`, structural validator, snapshot codec/store, typed `CheckInRouteDate`, and matching tests change together. Snapshots contain only complete validated post-gate stacks; `LaunchGate`, v2, malformed data, illegal parents, and illegal typed arguments are cleared to an in-memory LaunchGate fallback. A stored stack is a post-decision candidate, not a cold entry; incomplete onboarding ignores it. Initial obsolete cleanup and first v3 persist are serialized by the navigator, never independent composition effects. `ViewState`, domain/entity data, projections, and delivered results are never serialized. Incompatible format changes require an ADR and version bump. Platform I/O mechanics belong to the [Android](05-platform-android.md) and [iOS](06-platform-ios.md) owners.

## Dialog results

`MetricPicker` and `ConfirmDelete` are Nav3 dialog scenes. The host verifies the immediate typed caller, pops the dialog, queues a caller-scoped one-shot typed result, then awaits persistence of the resulting stack; recomposition can deliver the queued result to that live caller. Results do not enter route fields, `ViewState`, or snapshots. System/edge dismissal follows the same cancellation path. Delete confirmation completes the domain/Room delete before confirmed resolution is queued.

New screens follow [the Compose rule](../rules/compose.md). Use [common recipes](09-common-cases.md) for change order, not as a policy substitute.
