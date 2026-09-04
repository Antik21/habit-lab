package com.denis.habitlab.shared.presentation.navigation

import com.denis.habitlab.shared.domain.model.ExperimentId

/** One-shot caller-scoped result from the editor's modal metric picker. */
sealed interface MetricPickerResult {
    val experimentId: ExperimentId?

    data class Selected(
        override val experimentId: ExperimentId?,
        val metric: MetricKind,
    ) : MetricPickerResult

    data class Cancelled(override val experimentId: ExperimentId?) : MetricPickerResult
}

enum class MetricKind {
    DAILY_ENERGY,
    SLEEP_QUALITY,
}

/** One-shot result from the destructive dialog; the command has already completed on confirm. */
sealed interface DeleteDialogResult {
    val experimentId: ExperimentId

    data class Confirmed(override val experimentId: ExperimentId) : DeleteDialogResult

    data class Cancelled(override val experimentId: ExperimentId) : DeleteDialogResult
}
