package com.denis.habitlab.shared.domain.interactor

import com.denis.habitlab.shared.domain.model.CheckInOutcome
import com.denis.habitlab.shared.domain.model.DailyCheckIn
import com.denis.habitlab.shared.domain.model.Experiment
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.model.ExperimentName
import com.denis.habitlab.shared.domain.model.RecordedAt
import com.denis.habitlab.shared.domain.repository.CreateDraftResult
import com.denis.habitlab.shared.domain.repository.DeleteExperimentResult
import com.denis.habitlab.shared.domain.repository.EditDraftResult
import com.denis.habitlab.shared.domain.repository.ExperimentRepository
import com.denis.habitlab.shared.domain.repository.RecordDailyCheckInResult
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.time.Instant

class RecordDailyCheckInTest {
    @Test
    fun performedIntentCreatesAnOccurrenceFromTheRecordedTimestamp() = kotlinx.coroutines.runBlocking {
        val checkInDate = LocalDate.parse("2026-03-04")
        val recordedAt = recordedAt("2026-03-04T20:15:00Z", checkInDate)
        val repository = RecordingRepository()
        val command = RecordDailyCheckIn(repository, FixedRecordedAtSource(recordedAt))

        val result = command(ExperimentId("daily-movement"), checkInDate, DailyCheckInIntent.PERFORMED)

        val checkIn = assertNotNull(repository.recordedCheckIn)
        assertEquals(RecordDailyCheckInResult.Recorded(checkIn), result)
        val performed = assertIs<CheckInOutcome.Performed>(checkIn.outcome)
        assertEquals(recordedAt.utcInstant, performed.occurredAt.utcInstant)
        assertEquals(recordedAt.originalOffset, performed.occurredAt.originalOffset)
        assertEquals(checkInDate, performed.occurredAt.localDate)
        assertEquals(recordedAt, checkIn.recordedAt)
    }

    @Test
    fun performedIntentRejectsAStaleRouteDateBeforeWriting() = kotlinx.coroutines.runBlocking {
        val repository = RecordingRepository()
        val command = RecordDailyCheckIn(
            repository,
            FixedRecordedAtSource(recordedAt("2026-03-05T00:15:00Z", LocalDate.parse("2026-03-05"))),
        )
        val experimentId = ExperimentId("daily-movement")
        val staleDate = LocalDate.parse("2026-03-04")

        assertEquals(
            RecordDailyCheckInResult.InvalidPerformedDate(experimentId, staleDate),
            command(experimentId, staleDate, DailyCheckInIntent.PERFORMED),
        )
        assertNull(repository.recordedCheckIn)
    }

    @Test
    fun skippedIntentKeepsSubmissionTimeButCreatesNoOccurrence() = kotlinx.coroutines.runBlocking {
        val checkInDate = LocalDate.parse("2026-03-04")
        val recordedAt = recordedAt("2026-03-05T00:15:00Z", LocalDate.parse("2026-03-05"))
        val repository = RecordingRepository()
        val command = RecordDailyCheckIn(repository, FixedRecordedAtSource(recordedAt))

        val result = command(ExperimentId("daily-movement"), checkInDate, DailyCheckInIntent.SKIPPED)

        val checkIn = assertNotNull(repository.recordedCheckIn)
        assertEquals(RecordDailyCheckInResult.Recorded(checkIn), result)
        assertIs<CheckInOutcome.Skipped>(checkIn.outcome)
        assertEquals(checkInDate, checkIn.localDate)
        assertEquals(recordedAt, checkIn.recordedAt)
    }

    private class FixedRecordedAtSource(private val value: RecordedAt) : RecordedAtSource {
        override fun now(): RecordedAt = value
    }

    private class RecordingRepository : ExperimentRepository {
        var recordedCheckIn: DailyCheckIn? = null

        override suspend fun createDraft(draft: Experiment): CreateDraftResult = error("Unexpected create")

        override suspend fun editDraft(
            experimentId: ExperimentId,
            name: ExperimentName,
            updatedAt: RecordedAt,
        ): EditDraftResult = error("Unexpected edit")

        override suspend fun recordDailyCheckIn(checkIn: DailyCheckIn): RecordDailyCheckInResult {
            recordedCheckIn = checkIn
            return RecordDailyCheckInResult.Recorded(checkIn)
        }

        override suspend fun deleteExperiment(experimentId: ExperimentId): DeleteExperimentResult =
            error("Unexpected delete")
    }

    private fun recordedAt(instant: String, localDate: LocalDate) = RecordedAt(
        utcInstant = Instant.parse(instant),
        originalOffset = UtcOffset.ZERO,
        localDate = localDate,
    )
}
