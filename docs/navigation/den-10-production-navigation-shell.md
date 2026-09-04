# DEN-10 production navigation shell

## Ownership and entry boundaries

`shared.app.Navigation3AppHost` owns the single Navigation 3 back stack for Android and iOS. It is
the only code that pushes, pops, pops to root, opens a dialog, or resolves a dialog result.
`AppDestination` is the complete sealed serializable route set and contains only `ExperimentId` or
`FlowId` values.

Every Nav3 entry is wrapped by the saved-state and ViewModel-store decorators. The app composition
boundary then resolves a common AndroidX ViewModel from Koin for that entry. Each entry ViewModel has
an immutable Orbit `UiState` and emits one-shot `UiSideEffect`; it receives neither Koin nor a
Navigator. The common host collects the effects and validates every requested transition.

An experiment route passes its typed ID only. `ExperimentEntryViewModel` observes a current
`ExperimentProjection` via the domain `ExperimentProjectionObserver` abstraction. The present
in-memory implementation is a replaceable DEN-10 scaffold for a later persisted DEN-11 projection;
no destination restores serialized screen state.

## Dialog and safety behavior

Confirmation routes do not contain a result or caller stack. Confirm, explicit cancel, Android system
back, and the iOS leading-edge bridge all resolve through the common host. The dialog is popped first,
then a typed confirmed/cancelled result is queued for its immediate matching experiment caller before
the final route snapshot is awaited. After recomposition, the caller ViewModel receives it as an Orbit
side effect. The result is not a route field or `UiState` value.

External URLs accept only the two allowlisted experiment IDs. Bad URLs, stale effects, unsupported
flow IDs, malformed dialogs, and impossible transitions fail closed to Gallery. This preserves one
safe root on both platforms without giving a native host a second navigation stack.

The event bridge consumes an external event only after its host transition finishes, and only when
the event ID still matches. A remounted host therefore cannot replay a handled URL; a repeated live
URL has a new ID and is processed normally.

## Route-only restoration

The version 1 common snapshot stores only a valid ordered list of routes/typed IDs. The one
`AppNavigator` saves the final validated list immediately after every completed mutation, including
external navigation, dialog pop/result delivery, back, root fallback, and multi-step flow completion;
it never writes an intermediate state while an operation removes then adds entries. Android performs
serialized `SharedPreferences.commit()` writes on a dedicated background executor and awaits their
completion without performing commit disk I/O on the UI thread. The small initial preferences read is
synchronous during composition. If a replacement fails, it attempts to delete the snapshot on that
executor before returning. iOS writes the same payload behind `NSUserDefaults`. Normal Android Activity
recreation also continues to use Navigation 3's saved-state configuration.

The restore codec rejects and clears corrupt JSON, unknown versions, unknown experiment IDs,
overlong lists, unpaired flow steps, and dialogs without their matching caller; it then starts at
Gallery. `UiState`, observer projections, and completed dialog results are excluded. Entries re-read
their projection after restoration. A dialog visible at process interruption restores only as a valid
dialog route; no half-delivered outcome is replayed.

Storage durability is best-effort only when a platform cannot commit both a write and a subsequent
cleanup. Any surviving payload is still structurally validated during restore and safely falls back
to Gallery when invalid.

Android's existing handled-`ACTION_VIEW` marker still prevents a stale launch Intent from replacing a
restored stack, while a genuinely fresh URL takes precedence. The temporary iOS leading-edge adapter
remains the thin OS boundary documented in [ADR 0001](../adr/0001-navigation3-ios-edge-adapter.md).
The decision and remaining iOS 16 runtime validation risk are in
[ADR 0002](../adr/0002-navigation3-ios-restoration-runtime.md). Historical DEN-9 manual evidence is
preserved unchanged in [the DEN-9 spike record](../spikes/den-9-navigation3-kmp.md).

## Release validation

Before release, manually exercise Android and iOS forward/back, nested flow and pop-to-root, dialog
confirm/cancel/system-or-edge dismissal, malformed and repeat deep links, settings round trip, and
Activity/process relaunch from an experiment, both flow steps, and a dialog. Repeat Android's stale
`ACTION_VIEW` replay scenario after recreation. Validate the iOS 16 runtime when a device or
simulator is available. DEN-10 adds no automated navigation-plumbing tests.
