package com.denis.habitlab.shared.presentation.experimentlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denis.habitlab.shared.domain.observer.ExperimentListObserver
import kotlinx.coroutines.flow.collect
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

class ExperimentListViewModel(
    experimentListObserver: ExperimentListObserver,
    private val uiMapper: ExperimentListUiMapper,
) : ViewModel(), ContainerHost<ViewState, SideEffect> {
    override val container: Container<ViewState, SideEffect> = viewModelScope.container(
        initialState = ViewState(),
        onCreate = {
            experimentListObserver.observeAll().collect { observation ->
                reduce { uiMapper.map(observation) }
            }
        },
    )

    fun dispatchAction(action: Action) {
        when (action) {
            Action.CreateClicked -> onCreateClicked()
            Action.SettingsClicked -> onSettingsClicked()
            is Action.ExperimentClicked -> onExperimentClicked(action.experimentId)
        }
    }

    private fun onCreateClicked() = intent { postSideEffect(NavigationEffect.OpenCreateEditor) }

    private fun onSettingsClicked() = intent { postSideEffect(NavigationEffect.OpenSettings) }

    private fun onExperimentClicked(experimentId: com.denis.habitlab.shared.domain.model.ExperimentId) = intent {
        val available = state.content as? ContentUiModel.Available ?: return@intent
        if (available.experiments.any { it.id == experimentId }) {
            postSideEffect(NavigationEffect.OpenDetails(experimentId))
        }
    }
}
