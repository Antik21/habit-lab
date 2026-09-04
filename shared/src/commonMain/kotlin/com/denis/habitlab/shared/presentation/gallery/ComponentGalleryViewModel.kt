package com.denis.habitlab.shared.presentation.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denis.habitlab.shared.domain.observer.ExperimentListObserver
import com.denis.habitlab.shared.presentation.navigation.ExperimentId
import kotlinx.coroutines.flow.collect
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

class ComponentGalleryViewModel(
    experimentListObserver: ExperimentListObserver,
    private val uiMapper: ComponentGalleryUiMapper,
) : ViewModel(), ContainerHost<ViewState, SideEffect> {
    override val container: Container<ViewState, SideEffect> = viewModelScope.container(
        initialState = ViewState(),
        onCreate = {
            experimentListObserver.observeAll().collect { observation ->
                val mappedState = uiMapper.map(observation, habitName = state.habitName)
                reduce { mappedState }
            }
        },
    )

    fun dispatchAction(action: Action) {
        when (action) {
            Action.BackClicked -> onBackClicked()
            Action.DialogRequested -> onDialogRequested()
            Action.DialogConfirmed -> onDialogClosed()
            Action.DialogDismissed -> onDialogClosed()
            Action.StartFlowClicked -> onStartFlowClicked()
            is Action.HabitNameChanged -> onHabitNameChanged(action.value)
            is Action.ExperimentClicked -> onExperimentClicked(action.externalId)
        }
    }

    private fun onBackClicked() = intent { postSideEffect(NavigationEffect.Back) }

    private fun onDialogRequested() = intent { postSideEffect(ViewEffect.ShowDialog) }

    private fun onDialogClosed() = intent { postSideEffect(ViewEffect.HideDialog) }

    private fun onStartFlowClicked() = intent { postSideEffect(NavigationEffect.StartFlow) }

    private fun onHabitNameChanged(value: String) = intent {
        reduce { state.copy(habitName = value) }
    }

    private fun onExperimentClicked(externalId: String) = intent {
        val experimentId = ExperimentId.fromExternalValue(externalId)
        if (experimentId != null && state.experiments.contains(experimentId)) {
            postSideEffect(NavigationEffect.OpenExperiment(experimentId))
        } else {
            postSideEffect(NavigationEffect.Back)
        }
    }
}

private fun ExperimentsUiModel.contains(experimentId: ExperimentId): Boolean =
    this is ExperimentsUiModel.Available && when (experimentId.value) {
        "daily-movement" -> hasDailyMovement
        "sleep-routine" -> hasSleepRoutine
        else -> false
    }
