package com.denis.habitlab.shared.presentation.dailycheckin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denis.habitlab.shared.domain.interactor.DailyCheckInIntent
import com.denis.habitlab.shared.domain.interactor.RecordDailyCheckIn
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.observer.DailyCheckInObserver
import com.denis.habitlab.shared.domain.repository.RecordDailyCheckInResult
import kotlinx.coroutines.flow.collect
import kotlinx.datetime.LocalDate
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

class DailyCheckInViewModel(
    private val experimentId: ExperimentId,
    private val localDate: LocalDate,
    dailyCheckInObserver: DailyCheckInObserver,
    private val recordDailyCheckIn: RecordDailyCheckIn,
    private val uiMapper: DailyCheckInUiMapper,
) : ViewModel(), ContainerHost<ViewState, SideEffect> {
    override val container: Container<ViewState, SideEffect> = viewModelScope.container(
        initialState = ViewState(experimentId, localDate),
        onCreate = {
            dailyCheckInObserver.observe(experimentId, localDate).collect { observation ->
                reduce { uiMapper.map(observation, state) }
            }
        },
    )

    fun dispatchAction(action: Action) {
        when (action) {
            Action.BackClicked -> onBackClicked()
            Action.PerformedClicked -> onIntentSelected(DailyCheckInIntent.PERFORMED)
            Action.SkippedClicked -> onIntentSelected(DailyCheckInIntent.SKIPPED)
            Action.SaveClicked -> onSaveClicked()
        }
    }

    private fun onBackClicked() = intent { postSideEffect(NavigationEffect.Back) }

    private fun onIntentSelected(intent: DailyCheckInIntent) = intent {
        if (!state.isSaving && state.content == ContentUiModel.Ready) {
            reduce { state.copy(selectedIntent = intent, commandError = false) }
        }
    }

    private fun onSaveClicked() = intent {
        if (state.isSaving || state.content != ContentUiModel.Ready) return@intent
        reduce { state.copy(isSaving = true, commandError = false) }
        when (recordDailyCheckIn(experimentId, localDate, state.selectedIntent)) {
            is RecordDailyCheckInResult.Recorded -> postSideEffect(NavigationEffect.Back)
            is RecordDailyCheckInResult.Missing -> postSideEffect(NavigationEffect.PopToRoot)
            is RecordDailyCheckInResult.Failed,
            is RecordDailyCheckInResult.InvalidPerformedDate,
            -> reduce { state.copy(isSaving = false, commandError = true) }
        }
    }
}
