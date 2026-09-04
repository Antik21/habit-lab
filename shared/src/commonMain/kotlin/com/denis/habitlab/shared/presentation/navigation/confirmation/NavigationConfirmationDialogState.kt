package com.denis.habitlab.shared.presentation.navigation.confirmation

import androidx.compose.runtime.Immutable
import com.denis.habitlab.shared.presentation.navigation.ExperimentDialogResult

@Immutable
data class ViewState(
    val experimentId: String,
)

sealed interface Action {
    data object ConfirmClicked : Action
    data object CancelClicked : Action
}

sealed interface SideEffect

sealed interface NavigationEffect : SideEffect {
    data class Resolve(val result: ExperimentDialogResult) : NavigationEffect
}
