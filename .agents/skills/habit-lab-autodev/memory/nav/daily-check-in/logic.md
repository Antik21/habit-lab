# Daily Check-In logic

[`DailyCheckInViewModel`](../../../../../../shared/src/commonMain/kotlin/com/denis/habitlab/shared/presentation/dailycheckin/DailyCheckInViewModel.kt) combines projection and check-in observations for one typed ID/date. A missing experiment emits `PopToRoot`; read failure maps to a visible error. Outcome selection is ignored while saving or not ready and updates the selected semantic state.

Save converts the selected UI outcome to `DailyCheckInIntent`. Recorded data emits `Back`, while missing data emits `PopToRoot`; invalid date or storage failure clears saving and exposes a command error. [`AppNavigator.handleCheckInEffect`](../../../../../../shared/src/commonMain/kotlin/com/denis/habitlab/shared/app/Navigation3AppHost.kt) removes only the live typed check-in route on `Back`.
