package com.denis.habitlab.shared.presentation.navigation.flow.steptwo

import androidx.compose.runtime.Immutable
import com.denis.habitlab.shared.presentation.navigation.FlowId

@Immutable
data class ViewState(
    val flowId: String,
)

sealed interface Action {
    data object BackClicked : Action
    data object FinishClicked : Action
}

sealed interface SideEffect

sealed interface NavigationEffect : SideEffect {
    data object Back : NavigationEffect
    data class Complete(val flowId: FlowId) : NavigationEffect
    data object PopToRoot : NavigationEffect
}
