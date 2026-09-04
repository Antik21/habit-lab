package com.denis.habitlab.shared.presentation.gallery

import androidx.compose.runtime.Immutable
import com.denis.habitlab.shared.presentation.navigation.ExperimentId

@Immutable
data class ViewState(
    val habitName: String = "",
)

sealed interface Action {
    data object BackClicked : Action
    data object DialogRequested : Action
    data object DialogConfirmed : Action
    data object DialogDismissed : Action
    data object StartFlowClicked : Action
    data class HabitNameChanged(val value: String) : Action
    data class ExperimentClicked(val externalId: String) : Action
}

sealed interface SideEffect

sealed interface NavigationEffect : SideEffect {
    data class OpenExperiment(val experimentId: ExperimentId) : NavigationEffect
    data object StartFlow : NavigationEffect
    data object Back : NavigationEffect
}

sealed interface ViewEffect : SideEffect {
    data object ShowDialog : ViewEffect
    data object HideDialog : ViewEffect
}
