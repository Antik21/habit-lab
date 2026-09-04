package com.denis.habitlab.shared.presentation.confirmdelete

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denis.habitlab.shared.domain.interactor.DeleteExperiment
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.repository.DeleteExperimentResult
import com.denis.habitlab.shared.presentation.navigation.DeleteDialogResult
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

class ConfirmDeleteViewModel(
    private val experimentId: ExperimentId,
    private val deleteExperiment: DeleteExperiment,
) : ViewModel(), ContainerHost<ViewState, SideEffect> {
    override val container: Container<ViewState, SideEffect> = viewModelScope.container(ViewState(experimentId))
    private var hasResolved = false

    fun dispatchAction(action: Action) {
        when (action) {
            Action.ConfirmClicked -> onConfirmClicked()
            Action.CancelClicked -> onCancelClicked()
        }
    }

    private fun onConfirmClicked() = intent {
        if (state.isDeleting || hasResolved) return@intent
        reduce { state.copy(isDeleting = true, commandError = false) }
        when (deleteExperiment(experimentId)) {
            is DeleteExperimentResult.Deleted,
            is DeleteExperimentResult.Missing,
            -> {
                hasResolved = true
                postSideEffect(NavigationEffect.Resolve(DeleteDialogResult.Confirmed(experimentId)))
            }
            is DeleteExperimentResult.Failed -> reduce { state.copy(isDeleting = false, commandError = true) }
        }
    }

    private fun onCancelClicked() = intent {
        if (!state.isDeleting && !hasResolved) {
            hasResolved = true
            postSideEffect(NavigationEffect.Resolve(DeleteDialogResult.Cancelled(experimentId)))
        }
    }
}
