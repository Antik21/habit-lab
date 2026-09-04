package com.denis.habitlab.shared.data.local

import androidx.room3.Room
import com.denis.habitlab.shared.data.mapper.toDomain
import com.denis.habitlab.shared.data.mapper.toEntity
import com.denis.habitlab.shared.data.observer.RoomExperimentObservers
import com.denis.habitlab.shared.data.repository.RoomExperimentRepository
import com.denis.habitlab.shared.domain.model.CheckInOutcome
import com.denis.habitlab.shared.domain.model.DailyCheckIn
import com.denis.habitlab.shared.domain.model.Experiment
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.model.ExperimentName
import com.denis.habitlab.shared.domain.model.ExperimentStatus
import com.denis.habitlab.shared.domain.model.OccurredAt
import com.denis.habitlab.shared.domain.model.RecordedAt
import com.denis.habitlab.shared.domain.observer.DailyCheckInObservation
import com.denis.habitlab.shared.domain.observer.ExperimentListObservation
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObservation
import com.denis.habitlab.shared.domain.repository.CreateDraftResult
import com.denis.habitlab.shared.domain.repository.EditDraftResult
import com.denis.habitlab.shared.domain.repository.RecordDailyCheckInResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class RoomExperimentStoreTest {
    @Test
    fun writesReturnTypedResultsAndObserversReadTheSameRoomState() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val source = RoomExperimentLocalDataSource(database)
            val repository = RoomExperimentRepository(source)
            val observers = RoomExperimentObservers(source)
            val draft = draft(id = "draft-a123456", name = "Morning walk")
            val checkInDate = LocalDate.parse("2026-01-03")

            assertEquals(
                DailyCheckInObservation.Missing,
                observers.observe(draft.id, checkInDate).first(),
            )
            assertEquals(CreateDraftResult.Created(draft), repository.createDraft(draft))
            assertEquals(
                listOf(draft.id),
                assertIs<ExperimentListObservation.Available>(observers.observeAll().first())
                    .experiments
                    .map { it.id },
            )

            val editTimestamp = recordedAt("2026-01-03T10:01:00Z", checkInDate)
            val editedName = requireNotNull(ExperimentName.fromInput("Edited morning walk"))
            assertEquals(
                EditDraftResult.Updated(draft.id),
                repository.editDraft(draft.id, editedName, editTimestamp),
            )
            assertEquals(
                editedName.value,
                assertIs<ExperimentProjectionObservation.Available>(observers.observe(draft.id).first())
                    .projection
                    .displayName,
            )

            val performed = DailyCheckIn(
                experimentId = draft.id,
                localDate = checkInDate,
                outcome = CheckInOutcome.Performed(
                    OccurredAt(
                        utcInstant = Instant.parse("2026-01-03T09:30:00Z"),
                        originalOffset = UtcOffset(hours = 0),
                        localDate = checkInDate,
                    ),
                ),
                recordedAt = editTimestamp,
            )
            assertEquals(RecordDailyCheckInResult.Recorded(performed), repository.recordDailyCheckIn(performed))
            assertEquals(
                performed,
                assertIs<DailyCheckInObservation.Available>(
                    observers.observe(draft.id, checkInDate).first(),
                ).checkIn,
            )

            val missingId = ExperimentId("draft-b123456")
            assertEquals(
                EditDraftResult.Missing(missingId),
                repository.editDraft(missingId, editedName, editTimestamp),
            )
            assertEquals(
                RecordDailyCheckInResult.Missing(missingId),
                repository.recordDailyCheckIn(performed.copy(experimentId = missingId)),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun activeSlotIsAnActualRoomConstraintAndActiveRecordsCannotBeEditedAsDrafts() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val source = RoomExperimentLocalDataSource(database)
            val repository = RoomExperimentRepository(source)
            val active = experiment(
                id = "daily-movement",
                name = "Daily movement",
                status = ExperimentStatus.ACTIVE,
            )
            val secondActive = experiment(
                id = "draft-c123456",
                name = "Second active",
                status = ExperimentStatus.ACTIVE,
            )

            database.experimentDao().insertExperiment(active.toEntity())
            assertFails { database.experimentDao().insertExperiment(secondActive.toEntity()) }
            assertEquals(
                EditDraftResult.NotDraft(active.id),
                repository.editDraft(
                    experimentId = active.id,
                    name = requireNotNull(ExperimentName.fromInput("Renamed active")),
                    updatedAt = recordedAt("2026-01-03T10:00:00Z", LocalDate.parse("2026-01-03")),
                ),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun debugSeedIsDeterministicAndResetRestoresOnlyTheFixedFixture() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val source = RoomExperimentLocalDataSource(database)
            val observers = RoomExperimentObservers(source)
            val control = DebugExperimentDatabaseControl(source)

            assertTrue(source.seedIfEmpty())
            val seeded = assertIs<ExperimentListObservation.Available>(observers.observeAll().first())
            assertEquals(
                listOf("daily-movement", "sleep-routine"),
                seeded.experiments.map { it.id.value },
            )
            assertFalse(source.seedIfEmpty())
            source.createDraft(draft("draft-d123456", "User draft").toEntity())

            assertEquals(DebugDatabaseResetResult.Reset, control.resetAndSeed())
            val reset = assertIs<ExperimentListObservation.Available>(observers.observeAll().first())
            assertEquals(
                listOf("daily-movement", "sleep-routine"),
                reset.experiments.map { it.id.value },
            )
            assertEquals(
                DailyCheckInObservation.Available(DebugSeed.fixed.checkIns.first().toDomain()),
                observers.observe(ExperimentId("daily-movement"), LocalDate.parse("2026-01-02")).first(),
            )
        } finally {
            database.close()
        }
    }

    private fun inMemoryDatabase(): HabitLabDatabase = buildHabitLabDatabase(
        Room.inMemoryDatabaseBuilder<HabitLabDatabase>(HabitLabDatabaseConstructor::initialize),
    )

    private fun draft(id: String, name: String): Experiment = experiment(
        id = id,
        name = name,
        status = ExperimentStatus.DRAFT,
    )

    private fun experiment(id: String, name: String, status: ExperimentStatus): Experiment {
        val timestamp = recordedAt("2026-01-01T08:00:00Z", LocalDate.parse("2026-01-01"))
        return Experiment(
            id = ExperimentId(id),
            name = requireNotNull(ExperimentName.fromInput(name)),
            status = status,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }

    private fun recordedAt(instant: String, localDate: LocalDate): RecordedAt = RecordedAt(
        utcInstant = Instant.parse(instant),
        originalOffset = UtcOffset(hours = 0),
        localDate = localDate,
    )
}
