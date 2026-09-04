# ADR 0002: Common route-only navigation restoration

- Status: Accepted
- Scope: Android and iOS Navigation 3 restoration

## Context

DEN-9 established a common Navigation 3 back stack and verified that Android Activity recreation
restored a nested route. Its iOS terminate/relaunch probe returned to Gallery because SwiftUI did
not retain a common saved-state payload across process termination. Keeping a parallel Swift stack
would make Android and iOS resolve the same route differently and would give native code ownership
of common navigation.

Route restoration must not serialize a destination's rendered data. A screen model may be stale,
contain more information than a route needs, and cannot replace the destination's normal observer
read. Dialog outcomes are one-shot messages to a live caller, so they also cannot be a restored
screen state field.

## Decision

The common app shell owns one versioned, route-only snapshot. It stores only the ordered
`@Serializable AppDestination` keys, typed `ExperimentId` values, and the typed local-date route
argument used by Daily Check-In. The single navigator writes a validated, final post-operation stack
after every completed push, pop, root reset, dialog resolution, or external URL transition;
transitional remove/add states are never persisted. Android
serializes `SharedPreferences.commit()` calls on a dedicated background executor and awaits that
result without performing commit disk I/O on the UI thread; the small initial preferences read remains
synchronous during composition. A failed replacement attempts to remove the key on that same executor
so a known-stale valid route is not intentionally retained. iOS writes the same encoded payload through
`NSUserDefaults`. No Android Activity or Swift type mirrors the stack.

The restore codec accepts only version 2 snapshots that begin at `Gallery` (the retained wire-level
root for the Experiment List) and have a valid route sequence: allowlisted experiment IDs, an
editor created from Gallery or its matching details entry, a check-in with a parseable typed local
date above matching details, Settings directly above Gallery, and Metric Picker or Confirm Delete
immediately above their matching caller. Corrupt JSON, an unknown version, stale IDs, impossible
ordering, or an overlong stack is cleared and replaced with Gallery before Navigation 3 sees it.

Navigation 3's saved-state configuration remains in place for normal Android Activity recreation.
The compact capability snapshot covers Android/iOS process restoration. A fresh `ACTION_VIEW` URL
still takes precedence; Android's handled-URL marker prevents a stale launch Intent from overwriting
the restored route. The common bridge consumes only the successfully handled event ID, so remounting
a host cannot replay an old URL and a repeated live delivery remains distinct.

Entry `ViewState` is deliberately excluded. An entry receives a typed `ExperimentId` and re-reads its
current projection through `ExperimentProjectionObserver`. A confirmation result is delivered after
the dialog is popped to the immediate matching caller as an Orbit side effect. There is no pending
dialog result to restore: a process interrupted while a dialog is visible restores the valid dialog
route, and the user can explicitly confirm or cancel it.

## Consequences

Android system back and the temporary iOS leading-edge adapter continue to send the same common
back event into the app-owned stack. The native files stay boundaries for OS events and storage
only. The shell must retain the validator whenever a route is added, and a persisted-route version
must increase if a future route format becomes incompatible.

Durability remains best-effort if the platform cannot commit either the replacement or its cleanup;
the next restore still validates any surviving payload and falls back to Gallery when it is invalid.

The previous iOS 16 runtime limitation remains operational risk rather than a different policy: the
target builds for iOS 16.0, but a runtime validation still requires an installed iOS 16 simulator or
device. The current iOS 26.5 simulator and Android runtime should exercise process kill/relaunch
with details, editor/check-in, Settings, and dialog routes before release.
