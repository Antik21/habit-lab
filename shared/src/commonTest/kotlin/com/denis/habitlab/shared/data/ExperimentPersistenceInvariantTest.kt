package com.denis.habitlab.shared.data

import com.denis.habitlab.shared.data.local.CheckInEntity
import com.denis.habitlab.shared.data.local.ExperimentEntity
import com.denis.habitlab.shared.data.mapper.toDomain
import com.denis.habitlab.shared.data.mapper.toEntity
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

class ExperimentPersistenceInvariantTest {
    @Test
    fun identifiersAndNamesKeepExternalAndLocalRulesSeparate() {
        val draftId = ExperimentId("draft-a123456")

        assertEquals(draftId, ExperimentId.fromInternalValue(draftId.value))
        assertNull(ExperimentId.fromExternalValue(draftId.value))
        assertNull(ExperimentId.fromInternalValue("draft-short"))
        assertFailsWith<IllegalArgumentException> { ExperimentId("draft-short") }

        assertEquals("Evening walk", ExperimentName.fromInput("  Evening walk  ")?.value)
        assertNull(ExperimentName.fromInput("   "))
        assertNull(ExperimentName.fromInput("a".repeat(121)))
    }

    @Test
    fun timestampsRetainTheReportedDayAtTheirOriginalOffset() {
        val instant = Instant.parse("2026-01-01T22:30:00Z")
        val plusTwo = UtcOffset(hours = 2)

        val occurredAt = OccurredAt(
            utcInstant = instant,
            originalOffset = plusTwo,
            localDate = LocalDate.parse("2026-01-02"),
        )
        val recordedAt = RecordedAt(
            utcInstant = instant,
            originalOffset = plusTwo,
            localDate = LocalDate.parse("2026-01-02"),
        )

        assertEquals(LocalDate.parse("2026-01-02"), occurredAt.localDate)
        assertEquals(LocalDate.parse("2026-01-02"), recordedAt.localDate)
        assertFailsWith<IllegalArgumentException> {
            OccurredAt(
                utcInstant = instant,
                originalOffset = plusTwo,
                localDate = LocalDate.parse("2026-01-01"),
            )
        }
    }

    @Test
    fun performedAndSkippedCheckInsPreserveTheirDifferentMissingness() {
        val experimentId = ExperimentId("daily-movement")
        val date = LocalDate.parse("2026-01-02")
        val performed = DailyCheckIn(
            experimentId = experimentId,
            localDate = date,
            outcome = CheckInOutcome.Performed(
                OccurredAt(
                    utcInstant = Instant.parse("2026-01-02T18:00:00Z"),
                    originalOffset = UtcOffset(hours = 0),
                    localDate = date,
                ),
            ),
            recordedAt = recordedAt("2026-01-02T18:01:00Z", date),
        )
        val skipped = DailyCheckIn(
            experimentId = experimentId,
            localDate = date,
            outcome = CheckInOutcome.Skipped,
            recordedAt = recordedAt("2026-01-02T18:02:00Z", date),
        )

        val performedEntity = performed.toEntity()
        val skippedEntity = skipped.toEntity()

        assertEquals(performed, performedEntity.toDomain())
        assertEquals(skipped, skippedEntity.toDomain())
        assertEquals("PERFORMED", performedEntity.outcome)
        assertEquals("SKIPPED", skippedEntity.outcome)
        assertNull(skippedEntity.occurredUtcMillis)
        assertNull(skippedEntity.occurredOffsetSeconds)

        assertFailsWith<IllegalArgumentException> {
            DailyCheckIn(
                experimentId = experimentId,
                localDate = date,
                outcome = CheckInOutcome.Performed(
                    OccurredAt(
                        utcInstant = Instant.parse("2026-01-03T18:00:00Z"),
                        originalOffset = UtcOffset(hours = 0),
                        localDate = LocalDate.parse("2026-01-03"),
                    ),
                ),
                recordedAt = recordedAt("2026-01-02T18:01:00Z", date),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CheckInEntity(
                experimentId = experimentId.value,
                checkInLocalDate = date.toString(),
                outcome = "SKIPPED",
                occurredUtcMillis = Instant.parse("2026-01-02T18:00:00Z").toEpochMilliseconds(),
                occurredOffsetSeconds = 0,
                recordedUtcMillis = Instant.parse("2026-01-02T18:01:00Z").toEpochMilliseconds(),
                recordedOffsetSeconds = 0,
                recordedLocalDate = date.toString(),
            )
        }
    }

    @Test
    fun activeSlotMappingAndEntityValidationProtectTheSingleActiveConstraint() {
        val timestamp = recordedAt("2026-01-01T08:00:00Z", LocalDate.parse("2026-01-01"))
        val active = Experiment(
            id = ExperimentId("daily-movement"),
            name = requireNotNull(ExperimentName.fromInput("Daily movement")),
            status = ExperimentStatus.ACTIVE,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        val draft = active.copy(
            id = ExperimentId("draft-a123456"),
            status = ExperimentStatus.DRAFT,
        )

        assertEquals(1, active.toEntity().activeSlot)
        assertNull(draft.toEntity().activeSlot)
        assertEquals(active, active.toEntity().toDomain())
        assertEquals(draft, draft.toEntity().toDomain())
        assertFailsWith<IllegalArgumentException> {
            ExperimentEntity(
                id = active.id.value,
                displayName = active.name.value,
                status = "ACTIVE",
                activeSlot = null,
                createdUtcMillis = timestamp.utcInstant.toEpochMilliseconds(),
                createdOffsetSeconds = timestamp.originalOffset.totalSeconds,
                createdLocalDate = timestamp.localDate.toString(),
                updatedUtcMillis = timestamp.utcInstant.toEpochMilliseconds(),
                updatedOffsetSeconds = timestamp.originalOffset.totalSeconds,
                updatedLocalDate = timestamp.localDate.toString(),
            )
        }
    }

    private fun recordedAt(instant: String, localDate: LocalDate): RecordedAt = RecordedAt(
        utcInstant = Instant.parse(instant),
        originalOffset = UtcOffset(hours = 0),
        localDate = localDate,
    )
}
