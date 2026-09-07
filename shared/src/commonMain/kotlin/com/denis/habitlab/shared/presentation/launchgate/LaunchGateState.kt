package com.denis.habitlab.shared.presentation.launchgate

import androidx.compose.runtime.Immutable
@Immutable
data class ViewState(
    val content: ContentUiModel = ContentUiModel.Loading,
)

@Immutable
sealed interface ContentUiModel {
    data object Loading : ContentUiModel

    data object Invalid : ContentUiModel

    data object Failed : ContentUiModel
}

sealed interface Action {
    data object RetryClicked : Action
}

sealed interface SideEffect

sealed interface NavigationEffect : SideEffect {
    data class Resolve(val decision: LaunchGateDecision) : NavigationEffect

    /** Clears only a v2/malformed snapshot held by the navigator's serialized persistence path. */
    data object ClearObsoleteSnapshot : NavigationEffect
}
