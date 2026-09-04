package com.denis.habitlab.shared.domain.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * A route-safe experiment identifier. Public URLs retain a closed allowlist, while locally
 * generated drafts use the `draft-` form and can survive route restoration.
 */
@Serializable
data class ExperimentId(
    val value: String,
) {
    init {
        require(isSyntacticallyValid(value)) { "Invalid experiment ID: $value" }
    }

    companion object {
        private val externalValues = setOf("daily-movement", "sleep-routine")
        private val generatedDraftPattern = Regex("draft-[a-z0-9](?:[a-z0-9-]{6,62})")

        fun fromExternalValue(value: String): ExperimentId? =
            value.takeIf { it in externalValues }?.let(::ExperimentId)

        fun fromInternalValue(value: String): ExperimentId? =
            value.takeIf(::isSyntacticallyValid)?.let(::ExperimentId)

        fun isSyntacticallyValid(value: String): Boolean =
            value in externalValues || generatedDraftPattern.matches(value)
    }
}

@JvmInline
value class ExperimentName private constructor(
    val value: String,
) {
    companion object {
        fun fromInput(value: String): ExperimentName? =
            value.trim()
                .takeIf { it.length in 1..120 }
                ?.let(::ExperimentName)
    }
}

enum class ExperimentStatus {
    DRAFT,
    ACTIVE,
}

/** Persisted experiment record. A draft is represented by [status] rather than a misleading type. */
data class Experiment(
    val id: ExperimentId,
    val name: ExperimentName,
    val status: ExperimentStatus,
    val createdAt: RecordedAt,
    val updatedAt: RecordedAt,
)

data class ExperimentSummary(
    val id: ExperimentId,
    val name: ExperimentName,
    val status: ExperimentStatus,
)
