package com.denis.habitlab.shared.presentation.dailycheckin

import com.denis.habitlab.shared.domain.interactor.DailyCheckInIntent
import com.denis.habitlab.shared.domain.model.CheckInOutcome
import com.denis.habitlab.shared.domain.observer.DailyCheckInObservation

class DailyCheckInUiMapper {
    fun map(observation: DailyCheckInObservation, currentState: ViewState): ViewState = when (observation) {
        DailyCheckInObservation.Missing -> currentState.copy(
            content = ContentUiModel.Ready,
            hasExistingCheckIn = false,
        )
        is DailyCheckInObservation.Available -> currentState.copy(
            content = ContentUiModel.Ready,
            hasExistingCheckIn = true,
            selectedIntent = when (observation.checkIn.outcome) {
                is CheckInOutcome.Performed -> DailyCheckInIntent.PERFORMED
                CheckInOutcome.Skipped -> DailyCheckInIntent.SKIPPED
            },
        )
        is DailyCheckInObservation.Failed -> currentState.copy(content = ContentUiModel.Error)
    }
}
