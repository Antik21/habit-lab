package com.denis.habitlab.shared.presentation.dailycheckin

import com.denis.habitlab.shared.domain.model.CheckInOutcome
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.observer.DailyCheckInObservation
import kotlinx.datetime.LocalDate

class DailyCheckInUiMapper {
    fun initialState(
        experimentId: ExperimentId,
        localDate: LocalDate,
    ): ViewState = ViewState(
        experimentId = experimentId,
        localDateDisplay = localDate.toString(),
    )

    fun map(
        observation: DailyCheckInObservation,
        currentState: ViewState,
    ): ViewState = when (observation) {
        is DailyCheckInObservation.Available -> {
            val persistedOutcome = observation.checkIn.outcome.toPersistedOutcome()
            currentState.copy(
                content = ContentUiModel.Ready,
                selectedOutcome = if (currentState.hasHydratedInitialSelection) {
                    currentState.selectedOutcome
                } else {
                    persistedOutcome.toSelection()
                },
                hasHydratedInitialSelection = true,
                persistedOutcome = persistedOutcome,
            )
        }

        DailyCheckInObservation.Missing -> currentState.copy(
            content = ContentUiModel.Ready,
            hasHydratedInitialSelection = true,
            persistedOutcome = null,
        )

        is DailyCheckInObservation.Failed -> mapReadFailure(currentState)
    }

    fun mapReadFailure(currentState: ViewState): ViewState = currentState.copy(
        content = ContentUiModel.ReadError,
        isSaving = false,
    )

    private fun CheckInOutcome.toPersistedOutcome(): PersistedCheckInStatusUiModel = when (this) {
        is CheckInOutcome.Performed -> PersistedCheckInStatusUiModel.PERFORMED
        CheckInOutcome.Skipped -> PersistedCheckInStatusUiModel.SKIPPED
    }

    private fun PersistedCheckInStatusUiModel.toSelection(): CheckInSelectionUiModel = when (this) {
        PersistedCheckInStatusUiModel.PERFORMED -> CheckInSelectionUiModel.PERFORMED
        PersistedCheckInStatusUiModel.SKIPPED -> CheckInSelectionUiModel.SKIPPED
    }
}
