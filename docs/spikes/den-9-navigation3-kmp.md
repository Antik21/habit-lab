# DEN-9: Navigation 3 KMP compatibility spike

## Outcome

Navigation 3 works as the shared navigation foundation for this project on Android and iOS. The
retained spike uses Compose Multiplatform Navigation 3 UI `1.1.1`, one app-owned
`rememberNavBackStack`, and one `NavDisplay`. It is intentionally a compatibility harness for DEN-10,
not a complete production graph.

`AppDestination` is a `@Serializable sealed interface : NavKey`; route arguments are typed,
serializable IDs. An explicit `SavedStateConfiguration` registers every subtype through a polymorphic
serializers module, avoiding the Android-only reflection overload and making the route stack eligible
for common saved-state restoration on iOS.
View models and screens receive callbacks and do not own navigation state.

The gallery remains the root. Rows open typed experiments, the secondary action starts a two-entry
flow, and the existing primary action still opens the legacy gallery dialog. The experiment
confirmation is a `DialogSceneStrategy` entry. Its typed result is delivered to its calling experiment
only after that dialog entry is popped; system or gesture dismissal maps to `cancelled`.

Entry-owned screen actions are ignored unless their entry is `RESUMED`, which prevents duplicate
pushes while a transition is running. Platform back and external URL callbacks enter through distinct
common bridge signals and are not covered by that entry guard. `HabitLabDeepLink` accepts only these
exact routes:

- `habitlab://experiment/daily-movement`
- `habitlab://experiment/sleep-routine`

Missing, malformed, or unknown URLs reset safely to the gallery. Initial and repeat URL delivery use a
common event bridge. Android stores its handled `ACTION_VIEW` marker outside the incoming Intent so an
Activity recreation does not replay a stale URL. Opening native app settings does not mutate the stack.

On iOS, the SwiftUI host uses a narrow temporary leading-edge adapter. It translates one completed
native gesture into `AppNavigationEventBridge.requestBack()`; common `AppNavigator.onBack()` remains
the only stack mutation. See
[`../adr/0001-navigation3-ios-edge-adapter.md`](../adr/0001-navigation3-ios-edge-adapter.md).

## Verified QA matrix

Targets:

- Android Emulator `FO_Play_API36_1`, Android 16 / API 36.
- iPhone 17 Pro Simulator, iOS 26.5.
- The iOS deployment target remains 16.0 and builds successfully. No iOS 16 runtime was installed, so
  the lower bound was compile-tested but not runtime-tested.

| # | Scenario | Android | iOS |
| --- | --- | --- | --- |
| 1 | Gallery row opens `daily-movement` and shows its typed ID. | Pass | Pass |
| 2 | Nested flow reaches step 2; back returns step 2 → step 1 → caller. | Pass | Pass |
| 3 | Toolbar back, Android system back, and iOS leading-edge back use the common pop path. | Pass | Pass |
| 4 | Recreate/lifecycle exercise on flow step 2 and inspect the route. | Pass: Activity/configuration recreation restores step 2. | Partial: orientation preserves step 2; terminate/relaunch starts at Gallery. |
| 5 | Confirmation returns typed `confirmed`; cancel/dismiss returns typed `cancelled` after pop. | Pass | Pass |
| 6 | Allowed, malformed, unknown, and repeat live deep links behave safely. | Pass | Pass |
| 7 | Opening app settings and returning preserves the current experiment. | Pass | Pass |

The iOS matrix additionally dismisses the confirmation overlay with the leading-edge gesture. A
separate terminate/relaunch probe correctly failed the step-2 assertion and showed the Gallery root.
That negative result and the decision not to introduce a second persistence owner in this spike are
recorded in [`../adr/0002-navigation3-ios-restoration-runtime.md`](../adr/0002-navigation3-ios-restoration-runtime.md).

An Android regression flow also delivered deep link A, then B, recreated the Activity in both
orientations, and delivered B again. The typed B route remained current throughout, confirming that a
stale launch Intent is not replayed over the restored stack. A second probe backgrounded route B,
killed only the application process while retaining its task, and launched a new route A URL; the cold
Activity creation displayed typed route A.

The versioned matrix above is the retained observational test log. Each Pass was observed from a
completed Maestro/XCUITest flow on the named target; the Partial result was reproduced with a
controlled terminate/relaunch and a screenshot showing Gallery. Raw runner directories were
machine-local and are not cited as durable artifacts.

Stable selectors live in the closed `AutomationId` enum under `habitlab.gallery.*` and
`habitlab.navigation.*`; automation does not depend on display strings or runtime values.

## Build and test evidence

The final tree is verified with:

```sh
./gradlew :buildSrc:test check :androidApp:assembleDebug :shared:iosSimulatorArm64Test --rerun-tasks
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -configuration Debug build
```

Pure common tests cover the deep-link allowlist, malformed routes, repeated platform events, typed
dialog-result scoping, and distinct native back events. Device automation covers the seven behavioral
scenarios above.
