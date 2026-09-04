# ADR 0001: Temporary iOS leading-edge back adapter

- Status: Accepted
- Scope: DEN-9 Navigation 3 compatibility spike

## Context

Compose Multiplatform 1.12 and Navigation 3 UI 1.1.1 install a
`UIScreenEdgePanGestureRecognizer` for back handling. In the app's SwiftUI-embedded
`ComposeUIViewController`, simulator XCUITest/Maestro gestures did not dispatch that recognizer, even
though toolbar back and the same common Nav3 pop path worked. Repeated gestures with different start
points, distances, and durations reproduced the problem.

Leaving iOS gesture navigation unverified would not meet the spike's acceptance criteria. Introducing a
second UIKit navigation stack would create two sources of truth and undermine the KMP experiment.

## Decision

The iOS host wraps the Compose controller with a temporary `UIPanGestureRecognizer` adapter. It:

- begins only inside the semantic leading 24-point edge (left in LTR, right in RTL);
- accepts only an inward, predominantly horizontal gesture;
- completes after a 20%-width/48-point distance or 500-point-per-second velocity threshold;
- emits a single `requestBack()` event and never owns or mirrors route state;
- gives priority only over Compose's matching semantic-leading-edge back recognizer, leaving unrelated
  screen-edge recognizers untouched.

The common `AppNavigator.onBack()` remains the sole place that pops the Nav3 stack and resolves a
dialog dismissal as a typed cancellation.

## Consequences and removal

This adds a small UIKit boundary and does not provide an interactive transition animation. It was
validated on an iPhone 17 Pro Simulator running iOS 26.5, including nested navigation and dialog
dismissal. The project builds with deployment target iOS 16.0, but the minimum runtime could not be
device-tested because it was not installed.

Remove the adapter and its `requestBack()` bridge after upgrading Compose/Navigation 3 when the native
Compose edge recognizer passes the same simulator gesture flows in the SwiftUI embedding. The common
navigation stack and test selectors should remain unchanged during that migration.
