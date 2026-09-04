package com.denis.habitlab.shared.presentation.navigation

/** Typed, ephemeral return value delivered only to the experiment that opened the dialog. */
sealed interface ExperimentDialogResult {
    val experimentId: ExperimentId

    data class Confirmed(override val experimentId: ExperimentId) : ExperimentDialogResult

    data class Cancelled(override val experimentId: ExperimentId) : ExperimentDialogResult
}
