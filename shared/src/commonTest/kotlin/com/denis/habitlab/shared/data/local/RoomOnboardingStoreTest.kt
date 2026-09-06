package com.denis.habitlab.shared.data.local

import androidx.room3.Room
import com.denis.habitlab.shared.data.observer.RoomOnboardingObservers
import com.denis.habitlab.shared.data.repository.RoomOnboardingRepository
import com.denis.habitlab.shared.domain.model.ActiveOnboardingProtocol
import com.denis.habitlab.shared.domain.model.ConfirmedContextSelection
import com.denis.habitlab.shared.domain.model.ContextId
import com.denis.habitlab.shared.domain.model.EligibilityConfirmation
import com.denis.habitlab.shared.domain.model.GoalId
import com.denis.habitlab.shared.domain.model.HealthAccessOutcome
import com.denis.habitlab.shared.domain.model.HealthCapabilityValue
import com.denis.habitlab.shared.domain.model.HealthProviderAvailability
import com.denis.habitlab.shared.domain.model.ManualPlanState
import com.denis.habitlab.shared.domain.model.OnboardingAttemptId
import com.denis.habitlab.shared.domain.model.OnboardingHealthState
import com.denis.habitlab.shared.domain.model.OnboardingProgress
import com.denis.habitlab.shared.domain.model.OnboardingProtocolId
import com.denis.habitlab.shared.domain.model.OnboardingSelections
import com.denis.habitlab.shared.domain.model.OnboardingState
import com.denis.habitlab.shared.domain.model.OnboardingStep
import com.denis.habitlab.shared.domain.model.ProtocolTemplateId
import com.denis.habitlab.shared.domain.model.SetupDraftReference
import com.denis.habitlab.shared.domain.model.StoredContextSelection
import com.denis.habitlab.shared.domain.model.VisibleHealthRecordOutcome
import com.denis.habitlab.shared.domain.observer.ActiveOnboardingProtocolObservation
import com.denis.habitlab.shared.domain.observer.InvalidOnboardingPersistence
import com.denis.habitlab.shared.domain.observer.OnboardingCatalogObservation
import com.denis.habitlab.shared.domain.observer.OnboardingStateObservation
import com.denis.habitlab.shared.domain.repository.ActiveProtocolWriteResult
import com.denis.habitlab.shared.domain.repository.OnboardingPrecondition
import com.denis.habitlab.shared.domain.repository.OnboardingStorageFailure
import com.denis.habitlab.shared.domain.repository.OnboardingStorageOperation
import com.denis.habitlab.shared.domain.repository.OnboardingWriteResult
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs

class RoomOnboardingStoreTest {
    @Test
    fun catalogAndStateFlowsImmediatelyPublishEachAtomicCheckpointTransition() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val repository = repository(database)
            val observers = observers(database)
            assertEquals(expectedCatalog(), catalog(observers.observeCatalog().first()))

            observers.observeState().test {
                assertEquals(initialState(), state(awaitItem()))

                val eligibility = saved(repository.confirmEligibility())
                assertEquals(eligibility, state(awaitItem()))
                assertEquals(
                    OnboardingState(
                        eligibility = EligibilityConfirmation.CONFIRMED_ADULT,
                        progress = OnboardingProgress.InProgress(OnboardingStep.OUTCOME),
                        selections = OnboardingSelections(),
                    ),
                    eligibility,
                )
                val persistedEligibility = requireNotNull(database.onboardingDao().state())
                assertEquals(OnboardingWriteResult.Unchanged(eligibility), repository.confirmEligibility())
                expectNoEvents()
                assertEquals(persistedEligibility, database.onboardingDao().state())

                val goal = saved(repository.confirmGoal(GoalId.SLEEP_BETTER))
                assertEquals(goal, state(awaitItem()))
                assertEquals(OnboardingProgress.InProgress(OnboardingStep.CONTEXT), goal.progress)
                assertEquals(GoalId.SLEEP_BETTER, goal.selections.goal)

                val contexts = saved(repository.confirmContexts(ConfirmedContextSelection.ExplicitlyEmpty))
                assertEquals(contexts, state(awaitItem()))
                assertEquals(OnboardingProgress.InProgress(OnboardingStep.PROTOCOLS), contexts.progress)
                assertEquals(
                    StoredContextSelection(ConfirmedContextSelection.ExplicitlyEmpty, requiresConfirmation = false),
                    contexts.selections.contexts,
                )

                val template = saved(repository.selectProtocol(ProtocolTemplateId.AFTER_DINNER_WALK))
                assertEquals(template, state(awaitItem()))
                assertEquals(OnboardingProgress.InProgress(OnboardingStep.HEALTH_EXPLANATION), template.progress)

                val health = fullHealth(HealthAccessOutcome.FULL_ACCESS)
                val recordedHealth = saved(repository.saveHealthState(health))
                assertEquals(recordedHealth, state(awaitItem()))
                assertEquals(OnboardingProgress.InProgress(OnboardingStep.STATUS_COVERAGE), recordedHealth.progress)
                assertEquals(health, recordedHealth.selections.health)

                val draft = draft("attempt-1", 1)
                val setup = saved(repository.saveSetupDraftReference(draft))
                assertEquals(setup, state(awaitItem()))
                assertEquals(OnboardingProgress.InProgress(OnboardingStep.SETUP), setup.progress)
                assertEquals(draft, setup.selections.setupDraft)
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun preconditionsBlockEligibilityBypassAndChangedGoalPreservesButStalesContexts() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val repository = repository(database)
            val initial = state(observers(database).observeState().first())

            assertEquals(
                OnboardingWriteResult.Rejected(OnboardingPrecondition.ELIGIBILITY_CONFIRMED),
                repository.confirmGoal(GoalId.SLEEP_BETTER),
            )
            assertEquals(initial, state(observers(database).observeState().first()))
            assertEquals(
                OnboardingWriteResult.Rejected(OnboardingPrecondition.ELIGIBILITY_CONFIRMED),
                repository.confirmContexts(ConfirmedContextSelection.ExplicitlyEmpty),
            )

            saved(repository.confirmEligibility())
            assertEquals(
                OnboardingWriteResult.Rejected(OnboardingPrecondition.GOAL_CONFIRMED),
                repository.confirmContexts(ConfirmedContextSelection.ExplicitlyEmpty),
            )
            saved(repository.confirmGoal(GoalId.SLEEP_BETTER))
            assertEquals(
                OnboardingWriteResult.Rejected(OnboardingPrecondition.CONTEXTS_CONFIRMED_AND_CURRENT),
                repository.selectProtocol(ProtocolTemplateId.AFTER_DINNER_WALK),
            )

            val selectedContexts = ConfirmedContextSelection.Selected(setOf(ContextId.LATE_MEAL))
            saved(repository.confirmContexts(selectedContexts))
            saved(repository.selectProtocol(ProtocolTemplateId.AFTER_DINNER_WALK))
            saved(repository.saveHealthState(fullHealth(HealthAccessOutcome.PARTIAL_ACCESS)))
            saved(repository.saveSetupDraftReference(draft("attempt-1", 1)))

            val changed = saved(repository.confirmGoal(GoalId.WAKE_REFRESHED))
            assertEquals(OnboardingProgress.InProgress(OnboardingStep.CONTEXT), changed.progress)
            assertEquals(GoalId.WAKE_REFRESHED, changed.selections.goal)
            assertEquals(
                StoredContextSelection(selectedContexts, requiresConfirmation = true),
                changed.selections.contexts,
            )
            assertEquals(null, changed.selections.template)
            assertEquals(null, changed.selections.health)
            assertEquals(null, changed.selections.setupDraft)

            val sameGoal = saved(repository.confirmGoal(GoalId.WAKE_REFRESHED))
            assertEquals(changed.selections.contexts, sameGoal.selections.contexts)
            assertEquals(
                OnboardingWriteResult.Rejected(OnboardingPrecondition.CONTEXTS_CONFIRMED_AND_CURRENT),
                repository.selectProtocol(ProtocolTemplateId.AFTER_DINNER_WALK),
            )

            val reentry = repository.confirmEligibility()
            assertEquals(OnboardingWriteResult.Unchanged(sameGoal), reentry)
        } finally {
            database.close()
        }
    }

    @Test
    fun healthAxesPersistAllDistinctProviderAndAccessOutcomes() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val repository = repository(database)
            establishHealthPrerequisites(repository)
            val outcomes = listOf(
                HealthAccessOutcome.FULL_ACCESS,
                HealthAccessOutcome.PARTIAL_ACCESS,
                HealthAccessOutcome.NO_PERMISSION,
                HealthAccessOutcome.CANCELLED,
                HealthAccessOutcome.REQUEST_COMPLETED_WITHOUT_ACCESS_INFERENCE,
                HealthAccessOutcome.REQUEST_ERROR,
            )

            outcomes.forEach { accessOutcome ->
                val health = fullHealth(accessOutcome).copy(
                    providerAvailability = if (accessOutcome == HealthAccessOutcome.NO_PERMISSION) {
                        HealthProviderAvailability.UNAVAILABLE
                    } else {
                        HealthProviderAvailability.AVAILABLE
                    },
                    visibleRecords = when (accessOutcome) {
                        HealthAccessOutcome.FULL_ACCESS -> VisibleHealthRecordOutcome.RECORDS_VISIBLE
                        HealthAccessOutcome.PARTIAL_ACCESS -> VisibleHealthRecordOutcome.NO_VISIBLE_RECORDS
                        HealthAccessOutcome.REQUEST_ERROR -> VisibleHealthRecordOutcome.SOURCE_ERROR
                        else -> VisibleHealthRecordOutcome.UNKNOWN
                    },
                    manualPlan = if (accessOutcome == HealthAccessOutcome.CANCELLED) {
                        ManualPlanState.EXPLICITLY_SELECTED
                    } else {
                        ManualPlanState.NOT_SELECTED
                    },
                )
                assertEquals(health, saved(repository.saveHealthState(health)).selections.health)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun persistedContextAnswersKeepUnansweredEmptyAndOnlyNotSureYetDistinctAndRejectMixedRows() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val repository = repository(database)
            val observers = observers(database)
            assertEquals(null, state(observers.observeState().first()).selections.contexts)

            saved(repository.confirmEligibility())
            saved(repository.confirmGoal(GoalId.SLEEP_BETTER))
            val onlyNotSureYet = ConfirmedContextSelection.Selected(setOf(ContextId.NOT_SURE_YET))
            assertEquals(
                StoredContextSelection(onlyNotSureYet, requiresConfirmation = false),
                saved(repository.confirmContexts(onlyNotSureYet)).selections.contexts,
            )

            val persisted = requireNotNull(database.onboardingDao().state())
            database.onboardingDao().replaceState(
                persisted.copy(contextIds = "not-sure-yet,late-meal"),
            )
            assertEquals(
                OnboardingStateObservation.Invalid(
                    InvalidOnboardingPersistence("context_ids", "not-sure-yet mixed with other contexts"),
                ),
                observers.observeState().first(),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun setupDraftAcceptsOnlySameAttemptMonotonicRevisionsAndExactRetries() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val repository = repository(database)
            establishHealthPrerequisites(repository)
            val first = draft("attempt-1", 1)
            val second = draft("attempt-1", 2)

            assertEquals(first, saved(repository.saveSetupDraftReference(first)).selections.setupDraft)
            assertEquals(
                OnboardingWriteResult.Unchanged(state(observers(database).observeState().first())),
                repository.saveSetupDraftReference(first),
            )
            assertEquals(second, saved(repository.saveSetupDraftReference(second)).selections.setupDraft)
            assertEquals(
                OnboardingWriteResult.Rejected(OnboardingPrecondition.MONOTONIC_SETUP_DRAFT_REVISION),
                repository.saveSetupDraftReference(first),
            )
            assertEquals(
                OnboardingWriteResult.Rejected(OnboardingPrecondition.SETUP_DRAFT_MATCHES),
                repository.saveSetupDraftReference(draft("another-attempt", 3)),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun initialAndAppendedProtocolConfigurationsAreAtomicMonotonicAndIdempotent() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val repository = repository(database)
            val observers = observers(database)
            establishHealthPrerequisites(repository)
            val first = draft("attempt-1", 1)
            saved(repository.saveSetupDraftReference(first))
            val protocolId = protocol("onboarding-1")

            observers.observeActiveProtocol().test {
                assertEquals(ActiveOnboardingProtocolObservation.Missing, awaitItem())
                val created = assertIs<ActiveProtocolWriteResult.Created>(
                    repository.createInitialActiveProtocol(protocolId, first),
                ).protocol
                assertEquals(
                    ActiveOnboardingProtocolObservation.Available(created),
                    awaitItem(),
                )
                assertEquals(1, created.configuration.version)
                assertEquals(first, created.configuration.sourceSetupDraft)
                assertEquals(OnboardingProgress.Completed, state(observers.observeState().first()).progress)

                assertEquals(
                    ActiveProtocolWriteResult.Unchanged(created),
                    repository.createInitialActiveProtocol(protocol("new-candidate"), first),
                )
                assertFails {
                    database.onboardingDao().insertProtocol(
                        OnboardingProtocolEntity(
                            id = "second-active",
                            templateId = ProtocolTemplateId.CALM_EVENING_RITUAL.persistedValue,
                            status = ONBOARDING_ACTIVE_STATUS,
                            activeSlot = ONBOARDING_ACTIVE_SLOT,
                        ),
                    )
                }

                val second = draft("attempt-1", 2)
                saved(repository.saveSetupDraftReference(second))
                assertEquals(
                    ActiveProtocolWriteResult.ActiveProtocolAlreadyExists,
                    repository.createInitialActiveProtocol(protocol("nonmatching-candidate"), second),
                )
                val appended = assertIs<ActiveProtocolWriteResult.Appended>(
                    repository.appendProtocolConfiguration(protocolId, second),
                ).protocol
                assertEquals(2, appended.configuration.version)
                assertEquals(second, appended.configuration.sourceSetupDraft)
                assertEquals(OnboardingProgress.Completed, state(observers.observeState().first()).progress)

                assertEquals(
                    ActiveProtocolWriteResult.Unchanged(appended),
                    repository.appendProtocolConfiguration(protocolId, second),
                )
                assertEquals(
                    ActiveProtocolWriteResult.Rejected(OnboardingPrecondition.SETUP_DRAFT_MATCHES),
                    repository.appendProtocolConfiguration(protocolId, first),
                )
                assertEquals(
                    ActiveProtocolWriteResult.Rejected(OnboardingPrecondition.SETUP_DRAFT_MATCHES),
                    repository.appendProtocolConfiguration(protocolId, draft("another-attempt", 2)),
                )

                val third = draft("attempt-1", 3)
                saved(repository.saveSetupDraftReference(third))
                val appendedAgain = assertIs<ActiveProtocolWriteResult.Appended>(
                    repository.appendProtocolConfiguration(protocolId, third),
                ).protocol
                assertEquals(3, appendedAgain.configuration.version)
                assertEquals(third, appendedAgain.configuration.sourceSetupDraft)
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun activeProtocolDaoEmitsJoinedSnapshotWhoseConfigurationIsLatestAfterAppend() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val repository = repository(database)
            establishHealthPrerequisites(repository)
            val initialDraft = draft("snapshot-attempt", 1)
            val appendedDraft = draft("snapshot-attempt", 2)
            val protocolId = protocol("onboarding-snapshot")
            saved(repository.saveSetupDraftReference(initialDraft))

            database.onboardingDao().observeActiveProtocol().test {
                assertEquals(null, awaitItem())

                val created = assertIs<ActiveProtocolWriteResult.Created>(
                    repository.createInitialActiveProtocol(protocolId, initialDraft),
                ).protocol
                val createdSnapshot: ActiveOnboardingProtocolSnapshot = requireNotNull(awaitItem())
                assertEquals(protocolId.value, createdSnapshot.protocol.id)
                assertEquals(1, createdSnapshot.configuration?.version)
                assertEquals(initialDraft.attemptId.value, createdSnapshot.configuration?.sourceSetupDraftId)
                assertEquals(initialDraft.revision, createdSnapshot.configuration?.sourceSetupDraftRevision)
                assertEquals(created.configuration.version, createdSnapshot.configuration?.version)

                saved(repository.saveSetupDraftReference(appendedDraft))
                val appended = assertIs<ActiveProtocolWriteResult.Appended>(
                    repository.appendProtocolConfiguration(protocolId, appendedDraft),
                ).protocol
                val latestSnapshot: ActiveOnboardingProtocolSnapshot = requireNotNull(awaitItem())
                assertEquals(protocolId.value, latestSnapshot.protocol.id)
                assertEquals(2, latestSnapshot.configuration?.version)
                assertEquals(appendedDraft.attemptId.value, latestSnapshot.configuration?.sourceSetupDraftId)
                assertEquals(appendedDraft.revision, latestSnapshot.configuration?.sourceSetupDraftRevision)
                assertEquals(appended.configuration.version, latestSnapshot.configuration?.version)
                expectNoEvents()
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun activeProtocolObserverReportsGenuineMissingConfigurationAndRecoversAfterCleanup() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val observers = observers(database)
            observers.observeActiveProtocol().test {
                assertEquals(ActiveOnboardingProtocolObservation.Missing, awaitItem())

                database.onboardingDao().insertProtocol(
                    OnboardingProtocolEntity(
                        id = "raw-missing-configuration",
                        templateId = ProtocolTemplateId.AFTER_DINNER_WALK.persistedValue,
                        status = ONBOARDING_ACTIVE_STATUS,
                        activeSlot = ONBOARDING_ACTIVE_SLOT,
                    ),
                )
                assertEquals(
                    ActiveOnboardingProtocolObservation.Invalid(
                        InvalidOnboardingPersistence("onboarding_protocol.configuration", "missing"),
                    ),
                    awaitItem(),
                )

                database.onboardingDao().deleteAllProtocols()
                assertEquals(ActiveOnboardingProtocolObservation.Missing, awaitItem())
                expectNoEvents()
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun corruptPersistedIdsBecomeInvalidObservationsAndDatabaseFailuresStayTyped() = runBlocking {
        val database = inMemoryDatabase()
        val repository = repository(database)
        try {
            val current = requireNotNull(database.onboardingDao().state())
            database.onboardingDao().replaceState(current.copy(goalId = "unknown-goal"))
            assertEquals(
                OnboardingStateObservation.Invalid(InvalidOnboardingPersistence("goal_id", "unknown-goal")),
                observers(database).observeState().first(),
            )

            database.onboardingDao().replaceState(initialOnboardingStateEntity())
            database.onboardingDao().insertProtocol(
                OnboardingProtocolEntity(
                    id = "invalid-template",
                    templateId = "unknown-template",
                    status = ONBOARDING_ACTIVE_STATUS,
                    activeSlot = ONBOARDING_ACTIVE_SLOT,
                ),
            )
            database.onboardingDao().insertConfiguration(
                OnboardingProtocolConfigurationEntity(
                    protocolId = "invalid-template",
                    version = 1,
                    sourceSetupDraftId = "attempt-1",
                    sourceSetupDraftRevision = 1,
                ),
            )
            assertEquals(
                ActiveOnboardingProtocolObservation.Invalid(
                    InvalidOnboardingPersistence("onboarding_protocol.template_id", "unknown-template"),
                ),
                observers(database).observeActiveProtocol().first(),
            )

            database.close()
            assertEquals(
                OnboardingWriteResult.Failed(
                    OnboardingStorageFailure(OnboardingStorageOperation.CONFIRM_ELIGIBILITY),
                ),
                repository.confirmEligibility(),
            )
        } finally {
            database.close()
        }
    }

    private fun inMemoryDatabase(): HabitLabDatabase = buildHabitLabDatabase(
        Room.inMemoryDatabaseBuilder<HabitLabDatabase>(HabitLabDatabaseConstructor::initialize),
    )

    private fun repository(database: HabitLabDatabase) = RoomOnboardingRepository(
        RoomOnboardingLocalDataSource(database),
    )

    private fun observers(database: HabitLabDatabase) = RoomOnboardingObservers(
        RoomOnboardingLocalDataSource(database),
    )

    private suspend fun establishHealthPrerequisites(repository: RoomOnboardingRepository) {
        saved(repository.confirmEligibility())
        saved(repository.confirmGoal(GoalId.SLEEP_BETTER))
        saved(repository.confirmContexts(ConfirmedContextSelection.ExplicitlyEmpty))
        saved(repository.selectProtocol(ProtocolTemplateId.AFTER_DINNER_WALK))
        saved(repository.saveHealthState(fullHealth(HealthAccessOutcome.FULL_ACCESS)))
    }

    private fun saved(result: OnboardingWriteResult): OnboardingState =
        assertIs<OnboardingWriteResult.Saved>(result).state

    private fun state(observation: OnboardingStateObservation): OnboardingState =
        assertIs<OnboardingStateObservation.Available>(observation).state

    private fun catalog(observation: OnboardingCatalogObservation) =
        assertIs<OnboardingCatalogObservation.Available>(observation).catalog

    private fun draft(attempt: String, revision: Long): SetupDraftReference = SetupDraftReference(
        attemptId = requireNotNull(OnboardingAttemptId.fromPersisted(attempt)),
        revision = revision,
    )

    private fun protocol(value: String): OnboardingProtocolId =
        requireNotNull(OnboardingProtocolId.fromPersisted(value))

    private fun fullHealth(accessOutcome: HealthAccessOutcome) = OnboardingHealthState(
        capabilityValue = HealthCapabilityValue.AVAILABLE,
        providerAvailability = HealthProviderAvailability.AVAILABLE,
        accessOutcome = accessOutcome,
        visibleRecords = VisibleHealthRecordOutcome.RECORDS_VISIBLE,
    )

    private fun initialState() = OnboardingState(
        eligibility = EligibilityConfirmation.UNCONFIRMED,
        progress = OnboardingProgress.NotStarted,
        selections = OnboardingSelections(),
    )

    private fun expectedCatalog() = com.denis.habitlab.shared.domain.model.OnboardingCatalog(
        goals = GoalId.entries,
        contexts = ContextId.entries,
        templates = ProtocolTemplateId.entries.map { id ->
            com.denis.habitlab.shared.domain.model.ProtocolTemplate(
                id = id,
                displayName = when (id) {
                    ProtocolTemplateId.AFTER_DINNER_WALK -> "Прогулка после ужина"
                    ProtocolTemplateId.CALM_EVENING_RITUAL -> "Спокойный вечерний ритуал"
                    ProtocolTemplateId.REGULAR_SLEEP_SCHEDULE -> "Регулярное время сна"
                },
                manualCapable = true,
            )
        },
        metrics = com.denis.habitlab.shared.domain.model.MetricId.entries,
    )
}
