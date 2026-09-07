package com.denis.habitlab.shared.data.local

import androidx.room3.Room
import androidx.room3.executeSQL
import androidx.room3.useWriterConnection
import com.denis.habitlab.shared.data.observer.RoomOnboardingObservers
import com.denis.habitlab.shared.data.repository.RoomOnboardingRepository
import com.denis.habitlab.shared.domain.model.ActiveOnboardingProtocolCardinality
import com.denis.habitlab.shared.domain.model.ConfirmedContextSelection
import com.denis.habitlab.shared.domain.model.EligibilityConfirmation
import com.denis.habitlab.shared.domain.model.GoalId
import com.denis.habitlab.shared.domain.model.HealthAccessOutcome
import com.denis.habitlab.shared.domain.model.HealthCapabilityValue
import com.denis.habitlab.shared.domain.model.HealthProviderAvailability
import com.denis.habitlab.shared.domain.model.OnboardingAttemptId
import com.denis.habitlab.shared.domain.model.OnboardingHealthState
import com.denis.habitlab.shared.domain.model.OnboardingProgress
import com.denis.habitlab.shared.domain.model.OnboardingProtocolId
import com.denis.habitlab.shared.domain.model.OnboardingStep
import com.denis.habitlab.shared.domain.model.ProtocolTemplateId
import com.denis.habitlab.shared.domain.model.SetupDraftReference
import com.denis.habitlab.shared.domain.model.VisibleHealthRecordOutcome
import com.denis.habitlab.shared.domain.observer.InvalidOnboardingPersistence
import com.denis.habitlab.shared.domain.observer.LaunchGateSnapshotObservation
import com.denis.habitlab.shared.domain.repository.ActiveProtocolWriteResult
import com.denis.habitlab.shared.domain.repository.OnboardingWriteResult
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomLaunchGateSnapshotTest {
    @Test
    fun debugAutomationFixtureSeedsOneExactCompletedSnapshotWithoutIntermediateCorruptionOrDuplicates() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val fixture = DebugAutomationFixtureLocalDataSource(database)

            observers(database).observeLaunchSnapshot().test {
                val before = available(awaitItem())
                assertEquals(OnboardingProgress.NotStarted, before.state.progress)
                assertEquals(ActiveOnboardingProtocolCardinality.None, before.activeProtocol)

                assertTrue(fixture.seedIfNoExperiments())

                assertDebugFixtureSnapshot(available(awaitItem()))
                assertFalse(fixture.seedIfNoExperiments())
                assertEquals(2, database.experimentDao().totalExperimentCount())
                assertEquals(DebugSeed.fixed.experiments, database.experimentDao().observeExperiments().first())
                expectNoEvents()
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun debugAutomationControlResetRestoresTheExactFixtureWithOneAtomicObserverEmission() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val fixture = DebugAutomationFixtureLocalDataSource(database)
            assertTrue(fixture.seedIfNoExperiments())
            assertEquals(1, database.experimentDao().deleteExperiment("daily-movement"))
            assertEquals(1, database.experimentDao().totalExperimentCount())

            var resetCallbacks = 0
            val control = DebugExperimentDatabaseControl(
                localDataSource = RoomExperimentLocalDataSource(database),
                automationFixtureDataSource = fixture,
                onSuccessfulReset = { resetCallbacks += 1 },
            )

            observers(database).observeLaunchSnapshot().test {
                assertDebugFixtureSnapshot(available(awaitItem()))

                assertEquals(DebugDatabaseResetResult.Reset, control.resetAndSeed())

                assertDebugFixtureSnapshot(available(awaitItem()))
                assertEquals(1, resetCallbacks)
                assertEquals(DebugSeed.fixed.experiments, database.experimentDao().observeExperiments().first())
                expectNoEvents()
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun launchSnapshotPublishesNormalZeroAndOneActiveProtocolShapes() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val repository = repository(database)
            val observers = observers(database)
            val none = available(observers.observeLaunchSnapshot().first())
            assertEquals(EligibilityConfirmation.UNCONFIRMED, none.state.eligibility)
            assertEquals(ActiveOnboardingProtocolCardinality.None, none.activeProtocol)

            val source = establishSetupDraft(repository)
            val protocolId = protocol("onboarding-normal")
            assertIs<ActiveProtocolWriteResult.Created>(repository.createInitialActiveProtocol(protocolId, source))

            val one = available(observers.observeLaunchSnapshot().first())
            assertEquals(OnboardingProgress.Completed, one.state.progress)
            val active = assertIs<ActiveOnboardingProtocolCardinality.ExactlyOne>(one.activeProtocol).protocol
            assertEquals(protocolId, active.id)
            assertEquals(source, active.configuration.sourceSetupDraft)
        } finally {
            database.close()
        }
    }

    @Test
    fun launchSnapshotRejectsMissingStateMissingConfigurationAndInvalidStateMapping() = runBlocking {
        val database = inMemoryDatabase()
        try {
            database.useWriterConnection { connection -> connection.executeSQL("DELETE FROM onboarding_state") }
            assertInvalid(observers(database).observeLaunchSnapshot().first(), "onboarding_state", "missing")
        } finally {
            database.close()
        }

        val missingConfigurationDatabase = inMemoryDatabase()
        try {
            missingConfigurationDatabase.onboardingDao().insertProtocol(validProtocol("raw-missing-config", ONBOARDING_ACTIVE_SLOT))
            assertInvalid(
                observers(missingConfigurationDatabase).observeLaunchSnapshot().first(),
                "onboarding_protocol.active_slot",
                "one count has incomplete protocol data",
            )
        } finally {
            missingConfigurationDatabase.close()
        }

        val invalidMapperDatabase = inMemoryDatabase()
        try {
            val persisted = requireNotNull(invalidMapperDatabase.onboardingDao().state())
            invalidMapperDatabase.onboardingDao().replaceState(persisted.copy(goalId = "unknown-goal"))
            assertInvalid(
                observers(invalidMapperDatabase).observeLaunchSnapshot().first(),
                "goal_id",
                "unknown-goal",
            )
        } finally {
            invalidMapperDatabase.close()
        }
    }

    @Test
    fun launchSnapshotRejectsEveryActivityClaimThatIsNotAnActiveSlotPairAndImpossibleCardinality() = runBlocking {
        listOf(
            OnboardingProtocolEntity(
                id = "status-wrong-slot",
                templateId = ProtocolTemplateId.AFTER_DINNER_WALK.persistedValue,
                status = ONBOARDING_ACTIVE_STATUS,
                activeSlot = null,
            ),
            OnboardingProtocolEntity(
                id = "slot-wrong-status",
                templateId = ProtocolTemplateId.AFTER_DINNER_WALK.persistedValue,
                status = "DRAFT",
                activeSlot = ONBOARDING_ACTIVE_SLOT,
            ),
        ).forEach { invalidProtocol ->
            val database = inMemoryDatabase()
            try {
                database.onboardingDao().insertProtocol(invalidProtocol)
                database.onboardingDao().insertConfiguration(configuration(invalidProtocol.id))
                assertInvalid(
                    observers(database).observeLaunchSnapshot().first(),
                    "onboarding_protocol.status",
                    "${invalidProtocol.status}/${invalidProtocol.activeSlot}",
                )
            } finally {
                database.close()
            }
        }

        val cardinalityDatabase = inMemoryDatabase()
        try {
            cardinalityDatabase.onboardingDao().insertProtocol(validProtocol("active-claim-one", null))
            cardinalityDatabase.onboardingDao().insertProtocol(validProtocol("active-claim-two", null))
            assertInvalid(
                observers(cardinalityDatabase).observeLaunchSnapshot().first(),
                "onboarding_protocol.active_slot",
                "cardinality=2",
            )
        } finally {
            cardinalityDatabase.close()
        }
    }

    @Test
    fun createGuardReturnsExplicitConflictAndNeverInsertsADuplicateWhenAnyActivityClaimExists() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val repository = repository(database)
            val source = establishSetupDraft(repository)
            database.onboardingDao().insertProtocol(validProtocol("corrupt-active-claim", null))

            assertEquals(
                ActiveProtocolWriteResult.ActiveProtocolAlreadyExists,
                repository.createInitialActiveProtocol(protocol("new-candidate"), source),
            )
            assertNull(database.onboardingDao().protocol("new-candidate"))
            assertEquals(listOf("corrupt-active-claim"), database.onboardingDao().activeClaimingProtocols().map { it.id })
            assertEquals(
                OnboardingProgress.InProgress(OnboardingStep.SETUP),
                requireNotNull(database.onboardingDao().state()).toDomainProgress(),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun createTransactionEmitsOneCompletedSnapshotWithItsActiveProtocol() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val repository = repository(database)
            val source = establishSetupDraft(repository)
            val protocolId = protocol("transactional-launch")

            observers(database).observeLaunchSnapshot().test {
                val before = available(awaitItem())
                assertEquals(OnboardingProgress.InProgress(OnboardingStep.SETUP), before.state.progress)
                assertEquals(ActiveOnboardingProtocolCardinality.None, before.activeProtocol)

                assertIs<ActiveProtocolWriteResult.Created>(repository.createInitialActiveProtocol(protocolId, source))

                val after = available(awaitItem())
                assertEquals(OnboardingProgress.Completed, after.state.progress)
                val active = assertIs<ActiveOnboardingProtocolCardinality.ExactlyOne>(after.activeProtocol).protocol
                assertEquals(protocolId, active.id)
                assertEquals(source, active.configuration.sourceSetupDraft)
                expectNoEvents()
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun fileBackedLaunchSnapshotReopensAsTheSameCompletedOneActiveTransaction() = runBlocking {
        val fixture = FileBackedRoomDatabaseFixture()
        val source = draft("attempt-reopen-launch", 1)
        val protocolId = protocol("launch-reopen")
        try {
            val first = fixture.open()
            try {
                val repository = repository(first)
                establishSetupDraft(repository, source)
                assertIs<ActiveProtocolWriteResult.Created>(repository.createInitialActiveProtocol(protocolId, source))
            } finally {
                first.close()
            }
            val reopened = fixture.open()
            try {
                val snapshot = available(observers(reopened).observeLaunchSnapshot().first())
                assertEquals(OnboardingProgress.Completed, snapshot.state.progress)
                val active = assertIs<ActiveOnboardingProtocolCardinality.ExactlyOne>(snapshot.activeProtocol).protocol
                assertEquals(protocolId, active.id)
                assertEquals(source, active.configuration.sourceSetupDraft)
            } finally {
                reopened.close()
            }
        } finally {
            fixture.delete()
        }
    }

    private fun inMemoryDatabase(): HabitLabDatabase = buildHabitLabDatabase(
        Room.inMemoryDatabaseBuilder<HabitLabDatabase>(HabitLabDatabaseConstructor::initialize),
    )

    private fun repository(database: HabitLabDatabase) = RoomOnboardingRepository(RoomOnboardingLocalDataSource(database))

    private fun observers(database: HabitLabDatabase) = RoomOnboardingObservers(RoomOnboardingLocalDataSource(database))

    private suspend fun establishSetupDraft(
        repository: RoomOnboardingRepository,
        source: SetupDraftReference = draft(),
    ): SetupDraftReference {
        saved(repository.confirmEligibility())
        saved(repository.confirmGoal(GoalId.SLEEP_BETTER))
        saved(repository.confirmContexts(ConfirmedContextSelection.ExplicitlyEmpty))
        saved(repository.selectProtocol(ProtocolTemplateId.AFTER_DINNER_WALK))
        saved(repository.saveHealthState(health()))
        saved(repository.saveSetupDraftReference(source))
        return source
    }

    private fun validProtocol(id: String, activeSlot: Int?) = OnboardingProtocolEntity(
        id = id,
        templateId = ProtocolTemplateId.AFTER_DINNER_WALK.persistedValue,
        status = ONBOARDING_ACTIVE_STATUS,
        activeSlot = activeSlot,
    )

    private fun configuration(protocolId: String) = OnboardingProtocolConfigurationEntity(
        protocolId = protocolId,
        version = 1,
        sourceSetupDraftId = "attempt-raw",
        sourceSetupDraftRevision = 1,
    )

    private fun health() = OnboardingHealthState(
        capabilityValue = HealthCapabilityValue.AVAILABLE,
        providerAvailability = HealthProviderAvailability.AVAILABLE,
        accessOutcome = HealthAccessOutcome.FULL_ACCESS,
        visibleRecords = VisibleHealthRecordOutcome.RECORDS_VISIBLE,
    )

    private fun draft(attempt: String = "attempt-1", revision: Long = 1) = SetupDraftReference(
        attemptId = requireNotNull(OnboardingAttemptId.fromPersisted(attempt)),
        revision = revision,
    )

    private fun protocol(value: String): OnboardingProtocolId =
        requireNotNull(OnboardingProtocolId.fromPersisted(value))

    private fun saved(result: OnboardingWriteResult) = assertIs<OnboardingWriteResult.Saved>(result)

    private fun available(observation: LaunchGateSnapshotObservation) =
        assertIs<LaunchGateSnapshotObservation.Available>(observation).snapshot

    private fun assertDebugFixtureSnapshot(snapshot: com.denis.habitlab.shared.domain.model.LaunchGateSnapshot) {
        assertEquals(EligibilityConfirmation.CONFIRMED_ADULT, snapshot.state.eligibility)
        assertEquals(OnboardingProgress.Completed, snapshot.state.progress)
        assertEquals(ProtocolTemplateId.AFTER_DINNER_WALK, snapshot.state.selections.template)
        assertEquals(draft("debug-onboarding-attempt", 1), snapshot.state.selections.setupDraft)
        val active = assertIs<ActiveOnboardingProtocolCardinality.ExactlyOne>(snapshot.activeProtocol).protocol
        assertEquals(protocol("debug-onboarding-protocol"), active.id)
        assertEquals(snapshot.state.selections.template, active.template)
        assertEquals(snapshot.state.selections.setupDraft, active.configuration.sourceSetupDraft)
        assertEquals(1, active.configuration.version)
    }

    private fun assertInvalid(
        observation: LaunchGateSnapshotObservation,
        field: String,
        rawValue: String,
    ) {
        assertEquals(
            LaunchGateSnapshotObservation.Invalid(InvalidOnboardingPersistence(field, rawValue)),
            observation,
        )
    }

    private fun OnboardingStateEntity.toDomainProgress(): OnboardingProgress = when (progressKind) {
        PROGRESS_IN_PROGRESS -> OnboardingProgress.InProgress(
            requireNotNull(OnboardingStep.entries.firstOrNull { it.name == progressStep }),
        )
        PROGRESS_COMPLETED -> OnboardingProgress.Completed
        else -> error("Unexpected progress $progressKind/$progressStep")
    }
}
