package com.denis.habitlab.shared.presentation.onboardingcheckpoint

import com.denis.habitlab.shared.app.AppDestination
import com.denis.habitlab.shared.app.DeferredExternalNavigationCoordinator
import com.denis.habitlab.shared.app.ExternalNavigationEvent
import com.denis.habitlab.shared.app.OnboardingNavigationEffectHandler
import com.denis.habitlab.shared.app.TodayAdmissionRetryPolicy
import com.denis.habitlab.shared.app.admitTodayOrRollback
import com.denis.habitlab.shared.app.retryTodayAdmission
import com.denis.habitlab.shared.domain.interactor.ResolveLaunchGate
import com.denis.habitlab.shared.domain.model.ActiveOnboardingProtocol
import com.denis.habitlab.shared.domain.model.ActiveOnboardingProtocolCardinality
import com.denis.habitlab.shared.domain.model.ConfirmedContextSelection
import com.denis.habitlab.shared.domain.model.EligibilityConfirmation
import com.denis.habitlab.shared.domain.model.GoalId
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
import com.denis.habitlab.shared.domain.model.VersionedProtocolConfiguration
import com.denis.habitlab.shared.domain.observer.LaunchGateSnapshotObservation
import com.denis.habitlab.shared.domain.observer.LaunchGateSnapshotObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingCompletionMonitorTest {
    @Test
    fun incompleteOnboardingMonitorEmitsCompletionOnlyAfterAtomicSnapshotProvesTodayEligibility() = runTest {
        val monitor = OnboardingCompletionMonitor(
            snapshotObserver = snapshots(inProgressOnboardingSnapshot(), completedTodaySnapshot()),
            resolveLaunchGate = ResolveLaunchGate(),
        )

        assertEquals(
            listOf(NavigationEffect.OnboardingCompleted),
            monitor.observeCompletion().take(1).toList(),
        )
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun oneCompletionEmissionAutomaticallyRetriesSecondReplayAfterBackAndPreservesFullFifo() = runTest {
        val snapshotUpdates = MutableSharedFlow<LaunchGateSnapshotObservation>()
        val monitor = OnboardingCompletionMonitor(
            snapshotObserver = snapshots(snapshotUpdates),
            resolveLaunchGate = ResolveLaunchGate(),
        )
        val setup = AppDestination.OnboardingCheckpoint(OnboardingStep.SETUP)
        val destinations = mutableListOf<AppDestination>(AppDestination.Welcome, setup)
        val todayRoots = mutableListOf<List<AppDestination>>()
        val replayedLinks = mutableListOf<ExternalNavigationEvent>()
        val externalNavigation = DeferredExternalNavigationCoordinator()
        val firstDeferred = ExternalNavigationEvent(1, "habitlab://experiment/daily-movement")
        val secondDeferred = ExternalNavigationEvent(2, "habitlab://experiment/sleep-routine")
        var secondReplayFailures = 0
        val handler = OnboardingNavigationEffectHandler(
            isOriginCurrent = { destinations.lastOrNull() == it },
            onBack = { destinations.removeLast() },
            onSetupCompleted = {
                var stagedTodayRoutes = listOf<AppDestination>(AppDestination.Today)
                retryTodayAdmission(ImmediateRetryPolicy) {
                    externalNavigation.admitTodayOrRollback(
                        prepareToday = {
                            stagedTodayRoutes = listOf(AppDestination.Today)
                        },
                        rollback = {
                            stagedTodayRoutes = listOf(AppDestination.Today)
                        },
                        replayDeferredEvent = { event ->
                            replayedLinks += event
                            if (event == secondDeferred && secondReplayFailures++ == 0) {
                                error("controlled second replay failure")
                            }
                        },
                        commitToday = {
                            destinations.clear()
                            destinations += stagedTodayRoutes
                            todayRoots += destinations.toList()
                        },
                    )
                }
            },
        )
        val completionDelivery = backgroundScope.launch {
            monitor.observeCompletion().collect { completion -> handler.handle(setup, completion) }
        }
        runCurrent()

        handler.handle(setup, NavigationEffect.Back)
        assertEquals(listOf<AppDestination>(AppDestination.Welcome), destinations)
        externalNavigation.deferOrProcess(firstDeferred) { replayedLinks += it }
        externalNavigation.deferOrProcess(secondDeferred) { replayedLinks += it }

        snapshotUpdates.emit(completedTodaySnapshot())
        advanceUntilIdle()

        assertEquals(
            listOf<List<AppDestination>>(listOf(AppDestination.Today)),
            todayRoots,
        )
        assertEquals(listOf<AppDestination>(AppDestination.Today), destinations)
        assertEquals(
            listOf(firstDeferred, secondDeferred, firstDeferred, secondDeferred),
            replayedLinks,
        )
        completionDelivery.cancel()
    }

    private fun snapshots(vararg values: LaunchGateSnapshotObservation): LaunchGateSnapshotObserver =
        object : LaunchGateSnapshotObserver {
            override fun observeLaunchSnapshot(): Flow<LaunchGateSnapshotObservation> = flowOf(*values)
        }

    private fun snapshots(values: Flow<LaunchGateSnapshotObservation>): LaunchGateSnapshotObserver =
        object : LaunchGateSnapshotObserver {
            override fun observeLaunchSnapshot(): Flow<LaunchGateSnapshotObservation> = values
        }

    private fun inProgressOnboardingSnapshot() = LaunchGateSnapshotObservation.Available(
        com.denis.habitlab.shared.domain.model.LaunchGateSnapshot(
            state = state(OnboardingProgress.InProgress(OnboardingStep.OUTCOME)),
            activeProtocol = ActiveOnboardingProtocolCardinality.None,
        ),
    )

    private fun completedTodaySnapshot() = LaunchGateSnapshotObservation.Available(
        com.denis.habitlab.shared.domain.model.LaunchGateSnapshot(
            state = state(OnboardingProgress.Completed),
            activeProtocol = ActiveOnboardingProtocolCardinality.ExactlyOne(
                ActiveOnboardingProtocol(
                    id = requireNotNull(OnboardingProtocolId.fromPersisted("completion-protocol")),
                    template = ProtocolTemplateId.AFTER_DINNER_WALK,
                    configuration = VersionedProtocolConfiguration(1, draft()),
                ),
            ),
        ),
    )

    private fun state(progress: OnboardingProgress) = OnboardingState(
        eligibility = EligibilityConfirmation.CONFIRMED_ADULT,
        progress = progress,
        selections = OnboardingSelections(
            goal = GoalId.DAILY_MOVEMENT,
            contexts = StoredContextSelection(ConfirmedContextSelection.ExplicitlyEmpty, requiresConfirmation = false),
            template = ProtocolTemplateId.AFTER_DINNER_WALK,
            health = OnboardingHealthState(),
            setupDraft = draft(),
        ),
    )

    private fun draft() = SetupDraftReference(
        attemptId = requireNotNull(OnboardingAttemptId.fromPersisted("completion-attempt")),
        revision = 1,
    )

    private object ImmediateRetryPolicy : TodayAdmissionRetryPolicy {
        override val maximumAttempts: Int = 2

        override suspend fun awaitRetryAfterFailure(failedAttempt: Int) = Unit
    }
}
