package com.denis.habitlab.shared.domain.interactor

import com.denis.habitlab.shared.domain.model.DailyCheckIn
import com.denis.habitlab.shared.domain.model.Experiment
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.model.ExperimentName
import com.denis.habitlab.shared.domain.model.ExperimentStatus
import com.denis.habitlab.shared.domain.model.RecordedAt
import com.denis.habitlab.shared.domain.repository.CreateDraftResult
import com.denis.habitlab.shared.domain.repository.DeleteExperimentResult
import com.denis.habitlab.shared.domain.repository.EditDraftResult
import com.denis.habitlab.shared.domain.repository.ExperimentRepository
import com.denis.habitlab.shared.domain.repository.RecordDailyCheckInResult
import com.denis.habitlab.shared.domain.repository.StorageFailure
import com.denis.habitlab.shared.domain.repository.StorageOperation
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ExperimentCommandsTest {
    @Test
    fun createDraftUsesTheInjectedIdAndTimestampAndPropagatesTheTypedResult() = runTest {
        val generatedId = ExperimentId("draft-generate1")
        val timestamp = recordedAt("2026-04-01T18:30:00Z", "+04:00", "2026-04-01")
        val failure = StorageFailure(StorageOperation.CREATE_DRAFT)
        val repository = CapturingRepository(createResult = CreateDraftResult.Failed(failure))
        val command = CreateExperimentDraft(
            repository = repository,
            idSource = object : ExperimentIdSource {
                override fun nextDraftId(): ExperimentId = generatedId
            },
            recordedAtSource = FixedRecordedAtSource(timestamp),
        )
        val name = requireNotNull(ExperimentName.fromInput("  Evening movement  "))

        assertEquals(CreateDraftResult.Failed(failure), command(name))
        assertEquals(
            Experiment(
                id = generatedId,
                name = name,
                status = ExperimentStatus.DRAFT,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
            repository.createdDraft,
        )
    }

    @Test
    fun editDraftPassesTypedArgumentsAndTheInjectedTimestampThroughUnchanged() = runTest {
        val experimentId = ExperimentId("draft-edit123")
        val name = requireNotNull(ExperimentName.fromInput("Renamed experiment"))
        val timestamp = recordedAt("2026-04-01T18:30:00Z", "+04:00", "2026-04-01")
        val repository = CapturingRepository(editResult = EditDraftResult.NotDraft(experimentId))
        val command = EditExperimentDraft(repository, FixedRecordedAtSource(timestamp))

        assertEquals(EditDraftResult.NotDraft(experimentId), command(experimentId, name))
        assertEquals(EditCall(experimentId, name, timestamp), repository.editCall)
    }

    @Test
    fun deleteExperimentPropagatesTheRepositoryTypedResultForTheExactId() = runTest {
        val experimentId = ExperimentId("draft-delete2")
        val failure = StorageFailure(StorageOperation.DELETE_EXPERIMENT)
        val repository = CapturingRepository(deleteResult = DeleteExperimentResult.Failed(failure))

        assertEquals(DeleteExperimentResult.Failed(failure), DeleteExperiment(repository)(experimentId))
        assertEquals(experimentId, repository.deletedId)
    }

    private class FixedRecordedAtSource(private val value: RecordedAt) : RecordedAtSource {
        override fun now(): RecordedAt = value
    }

    private data class EditCall(
        val experimentId: ExperimentId,
        val name: ExperimentName,
        val updatedAt: RecordedAt,
    )

    private class CapturingRepository(
        private val createResult: CreateDraftResult? = null,
        private val editResult: EditDraftResult? = null,
        private val deleteResult: DeleteExperimentResult? = null,
    ) : ExperimentRepository {
        var createdDraft: Experiment? = null
        var editCall: EditCall? = null
        var deletedId: ExperimentId? = null

        override suspend fun createDraft(draft: Experiment): CreateDraftResult {
            createdDraft = draft
            return requireNotNull(createResult) { "Unexpected create" }
        }

        override suspend fun editDraft(
            experimentId: ExperimentId,
            name: ExperimentName,
            updatedAt: RecordedAt,
        ): EditDraftResult {
            editCall = EditCall(experimentId, name, updatedAt)
            return requireNotNull(editResult) { "Unexpected edit" }
        }

        override suspend fun recordDailyCheckIn(checkIn: DailyCheckIn): RecordDailyCheckInResult =
            error("Unexpected check-in")

        override suspend fun deleteExperiment(experimentId: ExperimentId): DeleteExperimentResult {
            deletedId = experimentId
            return requireNotNull(deleteResult) { "Unexpected delete" }
        }
    }

    private fun recordedAt(instant: String, offset: String, localDate: String): RecordedAt = RecordedAt(
        utcInstant = Instant.parse(instant),
        originalOffset = UtcOffset.parse(offset),
        localDate = LocalDate.parse(localDate),
    )
}
