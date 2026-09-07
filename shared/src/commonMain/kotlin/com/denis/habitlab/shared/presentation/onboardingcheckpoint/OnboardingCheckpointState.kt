package com.denis.habitlab.shared.presentation.onboardingcheckpoint

import androidx.compose.runtime.Immutable
import com.denis.habitlab.shared.domain.model.OnboardingStep
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId

@Immutable
data class ViewState(
    val checkpoint: CheckpointUiModel,
)

@Immutable
data class CheckpointUiModel(
    val step: OnboardingStep,
    val automationId: AutomationId,
)

sealed interface Action {
    data object BackClicked : Action
}

sealed interface SideEffect

sealed interface NavigationEffect : SideEffect {
    data object Back : NavigationEffect

    /** Posted by the app-owned SETUP monitor after an exact durable Today-ready snapshot. */
    data object OnboardingCompleted : NavigationEffect
}
