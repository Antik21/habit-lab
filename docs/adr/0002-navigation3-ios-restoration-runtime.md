# ADR 0002: iOS restoration and runtime validation limits

- Status: Accepted for the DEN-9 spike; revisit in DEN-10
- Scope: Navigation 3 state restoration on iOS

## Context

The common Navigation 3 stack uses typed serializable routes and an explicit
`SavedStateConfiguration`. Android restores flow step 2 across Activity/configuration recreation. On
the iOS 26.5 Simulator, the route remains visible across portrait/landscape lifecycle changes, but a
controlled terminate/relaunch probe starts from the Gallery root. The current SwiftUI/Compose host does
not persist the common saved-state payload across process termination.

The requested iOS 16+ runtime matrix also could not be completed at its lower bound because this machine
only has an iOS 26.5 Simulator runtime. An Xcode build targeting iOS 16.0 succeeds, with a linker warning
that the Compose-bundled ICU object was built for iOS Simulator 18.5.

## Options considered

1. Mirror the back stack in Swift or persist routes ad hoc in `UserDefaults` during the spike.
2. Treat process termination as a documented platform gap and keep the common stack as the only owner.
3. Remove the retained Navigation 3 foundation.

## Decision

Keep the minimal common Navigation 3 foundation and document the negative process-restoration result.
Do not add a second Swift-owned stack or an unscoped persistence format merely to make the spike pass.
The observed configuration/lifecycle behavior, typed serialization setup, and Android recreation result
are sufficient to carry the implementation into DEN-10 with the remaining risk explicit.

## Consequences and migration

Cold iOS relaunch currently returns to Gallery instead of restoring an in-progress nested flow. Before
DEN-10 promises process-death restoration, define the product's persisted-navigation policy and add one
common snapshot/restore boundary backed by platform storage, or adopt official Compose/Navigation 3
process-restoration support when available. The validation must then terminate the app process and
assert the typed route after relaunch on both the minimum supported iOS runtime and the current runtime.

The minimum-runtime warning must also be rechecked on an installed iOS 16 Simulator or physical device;
if the bundled dependency effectively requires iOS 18.5, raise the deployment target or select a
compatible dependency version before release.
