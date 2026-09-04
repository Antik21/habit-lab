package com.denis.habitlab.shared.presentation.navigation.flow.stepone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denis.habitlab.shared.presentation.navigation.FlowId
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

class NavigationFlowStepOneViewModel(
    private val flowId: FlowId,
) : ViewModel(), ContainerHost<ViewState, SideEffect> {
    override val container: Container<ViewState, SideEffect> = viewModelScope.container(
        initialState = ViewState(flowId = flowId.value),
        onCreate = {
            if (!FlowId.isSupported(flowId)) {
                postSideEffect(NavigationEffect.PopToRoot)
            }
        },
    )

    fun dispatchAction(action: Action) {
        when (action) {
            Action.BackClicked -> onBackClicked()
            Action.NextClicked -> onNextClicked()
        }
    }

    private fun onBackClicked() = intent { postSideEffect(NavigationEffect.Back) }

    private fun onNextClicked() = intent { postSideEffect(NavigationEffect.Advance(flowId)) }
}
