package com.denis.habitlab.shared.presentation.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denis.habitlab.shared.presentation.navigation.ExperimentId
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

class ComponentGalleryViewModel : ViewModel(), ContainerHost<ViewState, SideEffect> {
    override val container: Container<ViewState, SideEffect> = viewModelScope.container(ViewState())

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
        ExperimentId.fromExternalValue(externalId)?.let { experimentId ->
            postSideEffect(NavigationEffect.OpenExperiment(experimentId))
        } ?: postSideEffect(NavigationEffect.Back)
    }
}
