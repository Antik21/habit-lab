package com.denis.habitlab.shared.presentation.confirmdelete

import androidx.compose.runtime.Immutable
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.presentation.navigation.DeleteDialogResult

@Immutable
data class ViewState(
    val experimentId: ExperimentId,
    val isDeleting: Boolean = false,
    val commandError: Boolean = false,
)

sealed interface Action {
    data object ConfirmClicked : Action
    data object CancelClicked : Action
}

sealed interface SideEffect

sealed interface NavigationEffect : SideEffect { data class Resolve(val result: DeleteDialogResult) : NavigationEffect }
