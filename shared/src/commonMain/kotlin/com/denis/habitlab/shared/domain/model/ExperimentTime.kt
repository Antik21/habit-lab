package com.denis.habitlab.shared.domain.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.toLocalDateTime

/** The factual time and calendar day a user reports only when a habit was performed. */
data class OccurredAt(
    val utcInstant: Instant,
    val originalOffset: UtcOffset,
    val localDate: LocalDate,
) {
    init {
        require(localDate == utcInstant.toLocalDateTime(originalOffset.asTimeZone()).date) {
            "Local date must match the instant at its original UTC offset"
        }
    }
}

/** The timestamp when the entry was submitted, intentionally separate from factual [OccurredAt]. */
data class RecordedAt(
    val utcInstant: Instant,
    val originalOffset: UtcOffset,
    val localDate: LocalDate,
) {
    init {
        require(localDate == utcInstant.toLocalDateTime(originalOffset.asTimeZone()).date) {
            "Local date must match the instant at its original UTC offset"
        }
    }
}

sealed interface CheckInOutcome {
    data class Performed(val occurredAt: OccurredAt) : CheckInOutcome

    data object Skipped : CheckInOutcome
}

data class DailyCheckIn(
    val experimentId: ExperimentId,
    val localDate: LocalDate,
    val outcome: CheckInOutcome,
    val recordedAt: RecordedAt,
) {
    init {
        (outcome as? CheckInOutcome.Performed)?.let { performed ->
            require(performed.occurredAt.localDate == localDate) {
                "Performed occurrence date must equal the check-in local date"
            }
        }
    }
}
