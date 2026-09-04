package com.denis.habitlab.shared.presentation.navigation.experiment

import androidx.compose.runtime.Immutable
import com.denis.habitlab.shared.presentation.navigation.ExperimentDialogResult
import com.denis.habitlab.shared.presentation.navigation.ExperimentId
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import com.denis.habitlab.shared.presentation.ui.automation.NavigationSpikeAutomationIds

@Immutable
data class ViewState(
    val experimentId: String,
    val content: ContentUiModel = ContentUiModel.Loading,
)

@Immutable
sealed interface ContentUiModel {
    data object Loading : ContentUiModel
    data object Error : ContentUiModel
    data class Available(val displayName: String) : ContentUiModel
}

enum class NavigationDialogResultDisplay(
    val automationId: AutomationId,
) {
    Confirmed(NavigationSpikeAutomationIds.dialogResultConfirmed),
    Cancelled(NavigationSpikeAutomationIds.dialogResultCancelled),
}

sealed interface Action {
    data object BackClicked : Action
    data object OpenDialogClicked : Action
    data object StartFlowClicked : Action
    data object OpenSettingsClicked : Action
    data class DialogResultDelivered(val result: ExperimentDialogResult) : Action
}

sealed interface SideEffect

sealed interface NavigationEffect : SideEffect {
    data object Back : NavigationEffect
    data class OpenConfirmation(val experimentId: ExperimentId) : NavigationEffect
    data class StartFlow(val experimentId: ExperimentId) : NavigationEffect
    data class DialogResultDelivered(val result: ExperimentDialogResult) : NavigationEffect
    data object PopToRoot : NavigationEffect
}

sealed interface ViewEffect : SideEffect {
    data object OpenApplicationSettings : ViewEffect
}
