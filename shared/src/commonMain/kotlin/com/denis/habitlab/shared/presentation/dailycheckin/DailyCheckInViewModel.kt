package com.denis.habitlab.shared.presentation.dailycheckin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denis.habitlab.shared.domain.interactor.DailyCheckInIntent
import com.denis.habitlab.shared.domain.interactor.RecordDailyCheckIn
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.observer.DailyCheckInObserver
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObservation
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObserver
import com.denis.habitlab.shared.domain.repository.RecordDailyCheckInResult
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

class DailyCheckInViewModel(
    private val experimentId: ExperimentId,
    private val localDate: LocalDate,
    dailyCheckInObserver: DailyCheckInObserver,
    experimentProjectionObserver: ExperimentProjectionObserver,
    private val recordDailyCheckIn: RecordDailyCheckIn,
    private val uiMapper: DailyCheckInUiMapper,
) : ViewModel(), ContainerHost<ViewState, SideEffect> {
    override val container: Container<ViewState, SideEffect> = viewModelScope.container(
        initialState = uiMapper.initialState(experimentId, localDate),
        onCreate = {
            experimentProjectionObserver.observe(experimentId)
                .combine(dailyCheckInObserver.observe(experimentId, localDate)) { experiment, checkIn ->
                    experiment to checkIn
                }
                .collect { (experiment, checkIn) ->
                    when (experiment) {
                        is ExperimentProjectionObservation.Available -> reduce {
                            uiMapper.map(checkIn, state)
                        }

                        ExperimentProjectionObservation.Missing -> {
                            postSideEffect(NavigationEffect.PopToRoot)
                        }

                        is ExperimentProjectionObservation.Failed -> reduce {
                            uiMapper.mapReadFailure(state)
                        }
                    }
                }
        },
    )

    fun dispatchAction(action: Action) {
        when (action) {
            Action.BackClicked -> onBackClicked()
            Action.PerformedClicked -> onIntentSelected(CheckInSelectionUiModel.PERFORMED)
            Action.SkippedClicked -> onIntentSelected(CheckInSelectionUiModel.SKIPPED)
            Action.SaveClicked -> onSaveClicked()
        }
    }

    private fun onBackClicked() = intent { postSideEffect(NavigationEffect.Back) }

    private fun onIntentSelected(selected: CheckInSelectionUiModel) = intent {
        if (!state.isSaving && state.content == ContentUiModel.Ready) {
            reduce { state.copy(selectedOutcome = selected, commandError = false) }
        }
    }

    private fun onSaveClicked() = intent {
        if (state.isSaving || state.content != ContentUiModel.Ready) return@intent
        reduce { state.copy(isSaving = true, commandError = false) }
        when (recordDailyCheckIn(experimentId, localDate, state.selectedOutcome.toDomainIntent())) {
            is RecordDailyCheckInResult.Recorded -> postSideEffect(NavigationEffect.Back)
            is RecordDailyCheckInResult.Missing -> postSideEffect(NavigationEffect.PopToRoot)
            is RecordDailyCheckInResult.Failed,
            is RecordDailyCheckInResult.InvalidPerformedDate,
            -> reduce { state.copy(isSaving = false, commandError = true) }
        }
    }

    private fun CheckInSelectionUiModel.toDomainIntent(): DailyCheckInIntent = when (this) {
        CheckInSelectionUiModel.PERFORMED -> DailyCheckInIntent.PERFORMED
        CheckInSelectionUiModel.SKIPPED -> DailyCheckInIntent.SKIPPED
    }
}
