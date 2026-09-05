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
import com.denis.habitlab.shared.domain.repository.DeleteExperimentResult
import com.denis.habitlab.shared.domain.repository.EditDraftResult
import com.denis.habitlab.shared.domain.repository.RecordDailyCheckInResult
import com.denis.habitlab.shared.domain.repository.StorageFailure
import com.denis.habitlab.shared.domain.repository.StorageOperation
import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
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
    fun activeRoomObserversPublishEveryLocalWriteIncludingCheckInReplacementAndDeletionMissingness() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val source = RoomExperimentLocalDataSource(database)
            val repository = RoomExperimentRepository(source)
            val observers = RoomExperimentObservers(
                localDataSource = source,
                databaseReadiness = DatabaseReadiness(DatabaseReadinessState.Ready),
            )
            val draft = draft(id = "draft-observer1", name = "Initial name")
            val date = LocalDate.parse("2026-01-03")
            val renamed = requireNotNull(ExperimentName.fromInput("Renamed locally"))
            val performed = performedCheckIn(draft.id, date)
            val skipped = DailyCheckIn(
                experimentId = draft.id,
                localDate = date,
                outcome = CheckInOutcome.Skipped,
                recordedAt = recordedAt("2026-01-03T10:02:00Z", date),
            )

            observers.observeAll().test {
                val listEvents = this
                assertEquals(emptyList(), assertIs<ExperimentListObservation.Available>(awaitItem()).experiments)

                assertEquals(CreateDraftResult.Created(draft), repository.createDraft(draft))
                assertEquals(
                    listOf(draft.id),
                    assertIs<ExperimentListObservation.Available>(awaitItem()).experiments.map { it.id },
                )

                observers.observe(draft.id).test {
                    val projectionEvents = this
                    assertEquals(
                        "Initial name",
                        assertIs<ExperimentProjectionObservation.Available>(awaitItem()).projection.displayName,
                    )

                    assertEquals(
                        EditDraftResult.Updated(draft.id),
                        repository.editDraft(draft.id, renamed, recordedAt("2026-01-03T10:01:00Z", date)),
                    )
                    assertEquals(
                        renamed.value,
                        assertIs<ExperimentProjectionObservation.Available>(awaitItem()).projection.displayName,
                    )
                    assertEquals(
                        renamed.value,
                        assertIs<ExperimentListObservation.Available>(listEvents.awaitItem())
                            .experiments
                            .single()
                            .name
                            .value,
                    )

                    observers.observe(draft.id, date).test {
                        assertEquals(DailyCheckInObservation.Missing, awaitItem())

                        assertEquals(RecordDailyCheckInResult.Recorded(performed), repository.recordDailyCheckIn(performed))
                        assertEquals(DailyCheckInObservation.Available(performed), awaitItem())

                        assertEquals(RecordDailyCheckInResult.Recorded(skipped), repository.recordDailyCheckIn(skipped))
                        assertEquals(DailyCheckInObservation.Available(skipped), awaitItem())

                        assertEquals(DeleteExperimentResult.Deleted(draft.id), repository.deleteExperiment(draft.id))
                        assertEquals(DailyCheckInObservation.Missing, awaitItem())
                        assertEquals(ExperimentProjectionObservation.Missing, projectionEvents.awaitItem())
                        assertEquals(
                            emptyList(),
                            assertIs<ExperimentListObservation.Available>(listEvents.awaitItem())
                                .experiments,
                        )
                    }
                }
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun writesReturnTypedResultsAndObserversReadTheSameRoomState() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val source = RoomExperimentLocalDataSource(database)
            val repository = RoomExperimentRepository(source)
            val observers = RoomExperimentObservers(
                localDataSource = source,
                databaseReadiness = DatabaseReadiness(DatabaseReadinessState.Ready),
            )
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
    fun deleteMapsMissingAndDeletedResultsAndCascadesDependentCheckIns() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val source = RoomExperimentLocalDataSource(database)
            val repository = RoomExperimentRepository(source)
            val observers = RoomExperimentObservers(
                localDataSource = source,
                databaseReadiness = DatabaseReadiness(DatabaseReadinessState.Ready),
            )
            val draft = draft(id = "draft-delete1", name = "Delete me")
            val date = LocalDate.parse("2026-01-03")
            val checkIn = DailyCheckIn(
                experimentId = draft.id,
                localDate = date,
                outcome = CheckInOutcome.Skipped,
                recordedAt = recordedAt("2026-01-03T10:00:00Z", date),
            )

            assertEquals(CreateDraftResult.Created(draft), repository.createDraft(draft))
            assertEquals(RecordDailyCheckInResult.Recorded(checkIn), repository.recordDailyCheckIn(checkIn))

            assertEquals(DeleteExperimentResult.Deleted(draft.id), repository.deleteExperiment(draft.id))
            assertEquals(DailyCheckInObservation.Missing, observers.observe(draft.id, date).first())
            assertEquals(DeleteExperimentResult.Missing(draft.id), repository.deleteExperiment(draft.id))
        } finally {
            database.close()
        }
    }

    @Test
    fun debugSeedIsDeterministicAndResetRestoresOnlyTheFixedFixture() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val source = RoomExperimentLocalDataSource(database)
            val observers = RoomExperimentObservers(
                localDataSource = source,
                databaseReadiness = DatabaseReadiness(DatabaseReadinessState.Ready),
            )
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

    @Test
    fun debugBootstrapWaitsBeforeObserversCanPublishAnEmptyStore() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val source = RoomExperimentLocalDataSource(database)
            val readiness = DatabaseReadiness(DatabaseReadinessState.Initializing)
            val observers = RoomExperimentObservers(source, readiness)
            val observation = async(start = CoroutineStart.UNDISPATCHED) {
                observers.observeAll().first()
            }

            assertFalse(observation.isCompleted)
            DebugDatabaseBootstrap(DebugExperimentDatabaseControl(source), readiness).initialize()

            assertEquals(
                listOf("daily-movement", "sleep-routine"),
                assertIs<ExperimentListObservation.Available>(observation.await())
                    .experiments
                    .map { it.id.value },
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun activeObserverRecoversFailedReadinessWithFixedFixtureAfterDebugReset() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val transientSeedFailure = StorageFailure(StorageOperation.DEBUG_SEED)
            val readiness = DatabaseReadiness(DatabaseReadinessState.Failed(transientSeedFailure))
            val observers = RoomExperimentObservers(
                localDataSource = RoomExperimentLocalDataSource(database),
                databaseReadiness = readiness,
            )
            val control = DebugExperimentDatabaseControl(
                localDataSource = RoomExperimentLocalDataSource(database),
                onSuccessfulReset = readiness::markReady,
            )
            val failureObserved = CompletableDeferred<Unit>()
            val observations = async(start = CoroutineStart.UNDISPATCHED) {
                observers.observeAll()
                    .onEach { observation ->
                        if (observation == ExperimentListObservation.Failed(transientSeedFailure)) {
                            failureObserved.complete(Unit)
                        }
                    }
                    .take(2)
                    .toList()
            }

            failureObserved.await()
            assertEquals(DebugDatabaseResetResult.Reset, control.resetAndSeed())
            assertEquals(DatabaseReadinessState.Ready, readiness.state.value)
            val recoveredObservations = observations.await()
            assertEquals(
                ExperimentListObservation.Failed(transientSeedFailure),
                recoveredObservations.first(),
            )
            assertEquals(2, recoveredObservations.size)
            assertEquals(
                listOf("daily-movement", "sleep-routine"),
                assertIs<ExperimentListObservation.Available>(recoveredObservations[1])
                    .experiments
                    .map { it.id.value },
            )
            assertEquals(
                DailyCheckInObservation.Available(DebugSeed.fixed.checkIns.first().toDomain()),
                observers.observe(ExperimentId("daily-movement"), LocalDate.parse("2026-01-02")).first(),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun failedDebugResetDoesNotMarkReadinessReady() = runBlocking {
        val database = inMemoryDatabase()
        val transientSeedFailure = StorageFailure(StorageOperation.DEBUG_SEED)
        val readiness = DatabaseReadiness(DatabaseReadinessState.Failed(transientSeedFailure))
        val control = DebugExperimentDatabaseControl(
            localDataSource = RoomExperimentLocalDataSource(database),
            onSuccessfulReset = readiness::markReady,
        )
        try {
            database.close()

            assertIs<DebugDatabaseResetResult.Failed>(control.resetAndSeed())
            assertEquals(DatabaseReadinessState.Failed(transientSeedFailure), readiness.state.value)
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

    private fun performedCheckIn(experimentId: ExperimentId, date: LocalDate): DailyCheckIn = DailyCheckIn(
        experimentId = experimentId,
        localDate = date,
        outcome = CheckInOutcome.Performed(
            OccurredAt(
                utcInstant = Instant.parse("2026-01-03T09:30:00Z"),
                originalOffset = UtcOffset.ZERO,
                localDate = date,
            ),
        ),
        recordedAt = recordedAt("2026-01-03T10:00:00Z", date),
    )

    private fun recordedAt(instant: String, localDate: LocalDate): RecordedAt = RecordedAt(
        utcInstant = Instant.parse(instant),
        originalOffset = UtcOffset(hours = 0),
        localDate = localDate,
    )
}
