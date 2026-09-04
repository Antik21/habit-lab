package com.denis.habitlab.shared.presentation.navigation.experiment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObservation
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObserver
import com.denis.habitlab.shared.presentation.navigation.ExperimentDialogResult
import com.denis.habitlab.shared.presentation.navigation.ExperimentId
import kotlinx.coroutines.flow.collect
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

class NavigationExperimentViewModel(
    private val experimentId: ExperimentId,
    projectionObserver: ExperimentProjectionObserver,
    private val uiMapper: NavigationExperimentUiMapper,
) : ViewModel(), ContainerHost<ViewState, SideEffect> {
    override val container: Container<ViewState, SideEffect> = viewModelScope.container(
        initialState = ViewState(experimentId = experimentId.value),
        onCreate = {
            projectionObserver.observe(experimentId).collect { observation ->
                val mappedState = uiMapper.map(observation, fallbackExperimentId = experimentId)
                reduce { mappedState }
                if (observation == ExperimentProjectionObservation.Missing) {
                    postSideEffect(NavigationEffect.PopToRoot)
                }
            }
        },
    )

    fun dispatchAction(action: Action) {
        when (action) {
            Action.BackClicked -> onBackClicked()
            Action.OpenDialogClicked -> onOpenDialogClicked()
            Action.StartFlowClicked -> onStartFlowClicked()
            Action.OpenSettingsClicked -> onOpenSettingsClicked()
            is Action.DialogResultDelivered -> onDialogResultDelivered(action.result)
        }
    }

    private fun onBackClicked() = intent { postSideEffect(NavigationEffect.Back) }

    private fun onOpenDialogClicked() = intent {
        postSideEffect(NavigationEffect.OpenConfirmation(experimentId))
    }

    private fun onStartFlowClicked() = intent {
        postSideEffect(NavigationEffect.StartFlow(experimentId))
    }

    private fun onOpenSettingsClicked() = intent {
        postSideEffect(ViewEffect.OpenApplicationSettings)
    }

    private fun onDialogResultDelivered(result: ExperimentDialogResult) = intent {
        if (result.experimentId == experimentId) {
            postSideEffect(NavigationEffect.DialogResultDelivered(result))
        }
    }
}
