package com.denis.habitlab.shared.presentation.experimentdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denis.habitlab.shared.domain.interactor.GetCurrentLocalDate
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObservation
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObserver
import kotlinx.coroutines.flow.collect
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

class ExperimentDetailsViewModel(
    private val experimentId: ExperimentId,
    projectionObserver: ExperimentProjectionObserver,
    private val getCurrentLocalDate: GetCurrentLocalDate,
    private val uiMapper: ExperimentDetailsUiMapper,
) : ViewModel(), ContainerHost<ViewState, SideEffect> {
    override val container: Container<ViewState, SideEffect> = viewModelScope.container(
        initialState = ViewState(experimentId),
        onCreate = {
            projectionObserver.observe(experimentId).collect { observation ->
                reduce { uiMapper.map(observation, experimentId) }
                if (observation == ExperimentProjectionObservation.Missing) {
                    postSideEffect(NavigationEffect.PopToRoot)
                }
            }
        },
    )

    fun dispatchAction(action: Action) {
        when (action) {
            Action.BackClicked -> onBackClicked()
            Action.EditClicked -> onEditClicked()
            Action.DailyCheckInClicked -> onDailyCheckInClicked()
            Action.DeleteClicked -> onDeleteClicked()
            is Action.DeleteResultDelivered -> onDeleteResultDelivered(action.result)
        }
    }

    private fun onBackClicked() = intent { postSideEffect(NavigationEffect.Back) }

    private fun onEditClicked() = intent {
        if ((state.content as? ContentUiModel.Available)?.status == com.denis.habitlab.shared.presentation.model.ExperimentStatusUiModel.DRAFT) {
            postSideEffect(NavigationEffect.OpenEditor(experimentId))
        }
    }

    private fun onDailyCheckInClicked() = intent {
        if (state.content is ContentUiModel.Available) {
            postSideEffect(NavigationEffect.OpenDailyCheckIn(experimentId, getCurrentLocalDate()))
        }
    }

    private fun onDeleteClicked() = intent {
        if (state.content is ContentUiModel.Available) {
            postSideEffect(NavigationEffect.OpenConfirmDelete(experimentId))
        }
    }

    private fun onDeleteResultDelivered(result: com.denis.habitlab.shared.presentation.navigation.DeleteDialogResult) = intent {
        if (result.experimentId != experimentId) return@intent
        if (
            result is com.denis.habitlab.shared.presentation.navigation.DeleteDialogResult.Confirmed ||
            state.content == ContentUiModel.Loading
        ) {
            postSideEffect(NavigationEffect.PopToRoot)
        }
    }
}
