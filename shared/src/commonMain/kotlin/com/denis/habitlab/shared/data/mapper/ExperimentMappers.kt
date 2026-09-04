package com.denis.habitlab.shared.data.mapper

import com.denis.habitlab.shared.data.local.CheckInEntity
import com.denis.habitlab.shared.data.local.ExperimentEntity
import com.denis.habitlab.shared.domain.model.CheckInOutcome
import com.denis.habitlab.shared.domain.model.DailyCheckIn
import com.denis.habitlab.shared.domain.model.Experiment
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.model.ExperimentName
import com.denis.habitlab.shared.domain.model.ExperimentStatus
import com.denis.habitlab.shared.domain.model.OccurredAt
import com.denis.habitlab.shared.domain.model.RecordedAt
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import kotlin.math.abs
import kotlin.time.Instant

/** Explicit data-to-domain semantic mappings, kept outside Room's local-data boundary. */
internal fun ExperimentEntity.toDomain(): Experiment = Experiment(
    id = ExperimentId.fromInternalValue(id) ?: error("Persisted invalid experiment ID"),
    name = ExperimentName.fromInput(displayName) ?: error("Persisted invalid experiment name"),
    status = persistedStatus(),
    createdAt = RecordedAt(
        utcInstant = Instant.fromEpochMilliseconds(createdUtcMillis),
        originalOffset = createdOffsetSeconds.toUtcOffset(),
        localDate = LocalDate.parse(createdLocalDate),
    ),
    updatedAt = RecordedAt(
        utcInstant = Instant.fromEpochMilliseconds(updatedUtcMillis),
        originalOffset = updatedOffsetSeconds.toUtcOffset(),
        localDate = LocalDate.parse(updatedLocalDate),
    ),
)

internal fun Experiment.toEntity(): ExperimentEntity = ExperimentEntity(
    id = id.value,
    displayName = name.value,
    status = status.name,
    activeSlot = status.toActiveSlot(),
    createdUtcMillis = createdAt.utcInstant.toEpochMilliseconds(),
    createdOffsetSeconds = createdAt.originalOffset.totalSeconds,
    createdLocalDate = createdAt.localDate.toString(),
    updatedUtcMillis = updatedAt.utcInstant.toEpochMilliseconds(),
    updatedOffsetSeconds = updatedAt.originalOffset.totalSeconds,
    updatedLocalDate = updatedAt.localDate.toString(),
)

internal fun CheckInEntity.toDomain(): DailyCheckIn {
    val localDate = LocalDate.parse(checkInLocalDate)
    val outcome = when (outcome) {
        "PERFORMED" -> CheckInOutcome.Performed(occurredAtForPerformed(localDate))
        "SKIPPED" -> {
            check(occurredUtcMillis == null && occurredOffsetSeconds == null) {
                "Persisted skipped check-in must not have an occurrence timestamp"
            }
            CheckInOutcome.Skipped
        }

        else -> error("Persisted invalid check-in outcome")
    }
    return DailyCheckIn(
        experimentId = ExperimentId.fromInternalValue(experimentId) ?: error("Persisted invalid experiment ID"),
        localDate = localDate,
        outcome = outcome,
        recordedAt = RecordedAt(
            utcInstant = Instant.fromEpochMilliseconds(recordedUtcMillis),
            originalOffset = recordedOffsetSeconds.toUtcOffset(),
            localDate = LocalDate.parse(recordedLocalDate),
        ),
    )
}

internal fun DailyCheckIn.toEntity(): CheckInEntity = CheckInEntity(
    experimentId = experimentId.value,
    checkInLocalDate = localDate.toString(),
    outcome = when (outcome) {
        is CheckInOutcome.Performed -> "PERFORMED"
        CheckInOutcome.Skipped -> "SKIPPED"
    },
    occurredUtcMillis = (outcome as? CheckInOutcome.Performed)
        ?.occurredAt
        ?.utcInstant
        ?.toEpochMilliseconds(),
    occurredOffsetSeconds = (outcome as? CheckInOutcome.Performed)
        ?.occurredAt
        ?.originalOffset
        ?.totalSeconds,
    recordedUtcMillis = recordedAt.utcInstant.toEpochMilliseconds(),
    recordedOffsetSeconds = recordedAt.originalOffset.totalSeconds,
    recordedLocalDate = recordedAt.localDate.toString(),
)

private fun ExperimentEntity.persistedStatus(): ExperimentStatus = when (status) {
    ExperimentStatus.ACTIVE.name -> {
        check(activeSlot == ACTIVE_SLOT) { "Persisted active experiment has invalid active slot" }
        ExperimentStatus.ACTIVE
    }

    ExperimentStatus.DRAFT.name -> {
        check(activeSlot == null) { "Persisted draft experiment has an active slot" }
        ExperimentStatus.DRAFT
    }

    else -> error("Persisted invalid experiment status")
}

private fun ExperimentStatus.toActiveSlot(): Int? = when (this) {
    ExperimentStatus.ACTIVE -> ACTIVE_SLOT
    ExperimentStatus.DRAFT -> null
}

private fun CheckInEntity.occurredAtForPerformed(localDate: LocalDate): OccurredAt {
    val instant = requireNotNull(occurredUtcMillis) {
        "Persisted performed check-in is missing occurrence instant"
    }
    val offset = requireNotNull(occurredOffsetSeconds) {
        "Persisted performed check-in is missing occurrence offset"
    }
    return OccurredAt(
        utcInstant = Instant.fromEpochMilliseconds(instant),
        originalOffset = offset.toUtcOffset(),
        localDate = localDate,
    )
}

private fun Int.toUtcOffset(): UtcOffset {
    val sign = if (this < 0) -1 else 1
    val absoluteSeconds = abs(this)
    return UtcOffset(
        hours = sign * (absoluteSeconds / SECONDS_PER_HOUR),
        minutes = sign * ((absoluteSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE),
        seconds = sign * (absoluteSeconds % SECONDS_PER_MINUTE),
    )
}

private const val ACTIVE_SLOT = 1
private const val SECONDS_PER_HOUR = 60 * 60
private const val SECONDS_PER_MINUTE = 60
