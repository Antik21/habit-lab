package com.denis.habitlab.shared.presentation.navigation

import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import com.denis.habitlab.shared.presentation.ui.automation.NavigationSpikeAutomationIds

/** Typed, ephemeral return value delivered only to the experiment that opened the dialog. */
sealed interface ExperimentDialogResult {
    val experimentId: ExperimentId

    data class Confirmed(override val experimentId: ExperimentId) : ExperimentDialogResult

    data class Cancelled(override val experimentId: ExperimentId) : ExperimentDialogResult
}

enum class NavigationDialogResultDisplay(
    val automationId: AutomationId,
) {
    Confirmed(NavigationSpikeAutomationIds.dialogResultConfirmed),
    Cancelled(NavigationSpikeAutomationIds.dialogResultCancelled),
}
