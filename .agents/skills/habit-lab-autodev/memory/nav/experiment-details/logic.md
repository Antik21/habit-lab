# Experiment Details logic

[`ExperimentDetailsUiMapper.map`](../../../../../../shared/src/commonMain/kotlin/com/denis/habitlab/shared/presentation/experimentdetails/ExperimentDetailsUiMapper.kt) keeps the route `ExperimentId` while mapping available, loading, and error content. [`ExperimentDetailsViewModel`](../../../../../../shared/src/commonMain/kotlin/com/denis/habitlab/shared/presentation/experimentdetails/ExperimentDetailsViewModel.kt) pops to Gallery when its projection disappears.

The ViewModel permits edit only for a draft, obtains the local date before emitting `OpenDailyCheckIn`, and emits `OpenConfirmDelete` only for available content. A delete result with a different ID is ignored; confirmed delete (or loading during delivery) emits `PopToRoot`. [`AppNavigator.handleDetailsEffect`](../../../../../../shared/src/commonMain/kotlin/com/denis/habitlab/shared/app/Navigation3AppHost.kt) creates the typed child routes and binds the delete result to its immediate Details caller.
