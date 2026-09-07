package com.denis.habitlab.shared.presentation.onboardingcheckpoint

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denis.habitlab.shared.domain.model.OnboardingStep
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

class OnboardingCheckpointViewModel(
    step: OnboardingStep,
    uiMapper: OnboardingCheckpointUiMapper,
) : ViewModel(), ContainerHost<ViewState, SideEffect> {
    override val container: Container<ViewState, SideEffect> = viewModelScope.container(
        initialState = ViewState(uiMapper.map(step)),
    )

    fun dispatchAction(action: Action) {
        when (action) {
            Action.BackClicked -> intent { postSideEffect(NavigationEffect.Back) }
        }
    }
}
