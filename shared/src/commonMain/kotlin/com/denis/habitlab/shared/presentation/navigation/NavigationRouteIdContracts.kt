package com.denis.habitlab.shared.presentation.navigation

import kotlinx.serialization.Serializable

/** App routes consume IDs through presentation, while the actual value object belongs to domain. */
typealias ExperimentId = com.denis.habitlab.shared.domain.model.ExperimentId

/** Route-only ID for the small existing two-step setup flow. */
@Serializable
data class FlowId(
    val value: String,
) {
    companion object {
        private const val gallerySetup = "gallery-setup"
        private const val experimentPrefix = "experiment-"

        fun gallerySetup(): FlowId = FlowId(gallerySetup)

        fun forExperiment(experimentId: ExperimentId): FlowId =
            FlowId("$experimentPrefix${experimentId.value}")

        fun isSupported(flowId: FlowId): Boolean =
            flowId.value == gallerySetup || (
                flowId.value.startsWith(experimentPrefix) &&
                    ExperimentId.fromInternalValue(flowId.value.removePrefix(experimentPrefix)) != null
                )
    }
}
