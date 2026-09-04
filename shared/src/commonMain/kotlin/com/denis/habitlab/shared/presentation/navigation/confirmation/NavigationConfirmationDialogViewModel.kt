package com.denis.habitlab.shared.presentation.navigation.confirmation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denis.habitlab.shared.presentation.navigation.ExperimentDialogResult
import com.denis.habitlab.shared.presentation.navigation.ExperimentId
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

class NavigationConfirmationDialogViewModel(
    private val experimentId: ExperimentId,
) : ViewModel(), ContainerHost<ViewState, SideEffect> {
    override val container: Container<ViewState, SideEffect> = viewModelScope.container(
        ViewState(experimentId = experimentId.value),
    )
    private var hasResolved = false

    fun dispatchAction(action: Action) {
        when (action) {
            Action.ConfirmClicked -> onConfirmClicked()
            Action.CancelClicked -> onCancelClicked()
        }
    }

    private fun onConfirmClicked() {
        resolve(ExperimentDialogResult.Confirmed(experimentId))
    }

    private fun onCancelClicked() {
        resolve(ExperimentDialogResult.Cancelled(experimentId))
    }

    private fun resolve(result: ExperimentDialogResult) = intent {
        if (!hasResolved) {
            hasResolved = true
            postSideEffect(NavigationEffect.Resolve(result))
        }
    }
}
