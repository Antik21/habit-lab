package com.denis.habitlab.shared.core.navigation

import kotlinx.serialization.Serializable

/**
 * Stable identifier that may be placed in a route. The allowlist is intentionally shared by
 * internal navigation and external URL parsing until DEN-11 supplies the real catalogue.
 */
@Serializable
data class ExperimentId(
    val value: String,
) {
    init {
        require(value in knownValues) { "Unsupported experiment ID: $value" }
    }

    companion object {
        private val knownValues = setOf("daily-movement", "sleep-routine")

        fun fromExternalValue(value: String): ExperimentId? =
            value.takeIf { it in knownValues }?.let(::ExperimentId)
    }
}

/** Route-only ID for the temporary two-step setup flow. */
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
                    ExperimentId.fromExternalValue(flowId.value.removePrefix(experimentPrefix)) != null
                )
    }
}
