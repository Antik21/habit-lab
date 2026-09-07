package com.denis.habitlab.shared.app

import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeferredExternalNavigationCoordinatorTest {
    @Test
    fun olderDeferredLinksReplayBeforeArrivalDuringSuspendedTodayPersistenceExactlyOnce() = runTest {
        val coordinator = DeferredExternalNavigationCoordinator()
        val processed = mutableListOf<ExternalNavigationEvent>()
        val olderDeferred = ExternalNavigationEvent(1, "habitlab://experiment/daily-movement")
        val arrivalDuringTransition = ExternalNavigationEvent(2, "habitlab://experiment/sleep-routine")
        val persistenceStarted = CompletableDeferred<Unit>()
        val resumePersistence = CompletableDeferred<Unit>()

        coordinator.deferOrProcess(olderDeferred) { processed += it }

        val transition = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.admitToday(
                prepareToday = {
                    persistenceStarted.complete(Unit)
                    resumePersistence.await()
                },
                replayDeferredEvent = { processed += it },
                commitToday = {},
            )
        }
        persistenceStarted.await()

        val arrival = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.deferOrProcess(arrivalDuringTransition) { processed += it }
        }
        assertFalse(arrival.isCompleted)

        resumePersistence.complete(Unit)
        transition.await()
        arrival.await()

        assertEquals(listOf(olderDeferred, arrivalDuringTransition), processed)
    }

    @Test
    fun backWaitsForQueuedPostOnboardingReplayToCommitBeforeItStagesItsOwnMutation() = runTest {
        val navigationMutations = NavigationMutationMutex()
        val coordinator = DeferredExternalNavigationCoordinator(navigationMutations::runAtomically)
        val deferred = ExternalNavigationEvent(1, "habitlab://experiment/daily-movement")
        val replayStarted = CompletableDeferred<Unit>()
        val releaseReplay = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val visibleRoutes = mutableListOf<AppDestination>(AppDestination.Welcome)

        coordinator.deferOrProcess(deferred) { error("event must remain queued before Today admission") }
        val admission = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.admitToday(
                prepareToday = { order += "prepare" },
                replayDeferredEvent = {
                    order += "replay"
                    replayStarted.complete(Unit)
                    releaseReplay.await()
                },
                commitToday = {
                    order += "commit"
                    visibleRoutes.clear()
                    visibleRoutes += AppDestination.Today
                    visibleRoutes += AppDestination.Settings
                },
            )
        }
        replayStarted.await()

        val back = async(start = CoroutineStart.UNDISPATCHED) {
            navigationMutations.runAtomically {
                val stagedRoutes = listOf<AppDestination>(AppDestination.Today)
                persistThenPublish(
                    persistStagedRoute = {
                        assertEquals(
                            listOf<AppDestination>(AppDestination.Today, AppDestination.Settings),
                            visibleRoutes,
                        )
                        order += "back-stage"
                    },
                    publishVisibleRoute = {
                        order += "back-publish"
                        visibleRoutes.clear()
                        visibleRoutes += stagedRoutes
                    },
                )
            }
        }

        assertFalse(back.isCompleted)
        assertEquals(listOf<AppDestination>(AppDestination.Welcome), visibleRoutes)

        releaseReplay.complete(Unit)
        admission.await()
        back.await()

        assertEquals(
            listOf("prepare", "replay", "commit", "back-stage", "back-publish"),
            order,
        )
        assertEquals(listOf<AppDestination>(AppDestination.Today), visibleRoutes)
    }

    @Test
    fun bestEffortOrdinaryCreateAndResultPublishOnceWhenSnapshotWriteReportsFailure() = runTest {
        val navigationMutations = NavigationMutationMutex()
        val store = AndroidLikeFailingNavigationRouteStore()
        val visibleRoutes = mutableListOf<AppDestination>(AppDestination.Today)
        var resultDeliveries = 0

        navigationMutations.runAtomically {
            val stagedRoutes = visibleRoutes + AppDestination.Settings
            persistThenPublish(
                persistStagedRoute = {
                    NavigationRouteSnapshotCodec.persist(store, stagedRoutes, durable = false)
                },
                publishVisibleRoute = {
                    visibleRoutes.clear()
                    visibleRoutes += stagedRoutes
                },
            )
        }

        navigationMutations.runAtomically {
            val stagedRoutes = listOf<AppDestination>(AppDestination.Today)
            persistThenPublish(
                persistStagedRoute = {
                    NavigationRouteSnapshotCodec.persist(store, stagedRoutes, durable = false)
                },
                publishVisibleRoute = {
                    visibleRoutes.clear()
                    visibleRoutes += stagedRoutes
                    resultDeliveries += 1
                },
            )
        }

        assertEquals(listOf<AppDestination>(AppDestination.Today), visibleRoutes)
        assertEquals(1, resultDeliveries)
        assertEquals(2, store.bestEffortWrites)
        NavigationRouteSnapshotCodec.clear(store, durable = false)
        assertEquals(1, store.bestEffortClears)
    }

    @Test
    fun bestEffortEligibleExternalUrlPublishesOnceWhenSnapshotWriteReportsFailure() = runTest {
        val navigationMutations = NavigationMutationMutex()
        val store = AndroidLikeFailingNavigationRouteStore()
        val visibleRoutes = mutableListOf<AppDestination>(AppDestination.Today)
        val route = listOf(
            AppDestination.Today,
            AppDestination.Experiment(ExperimentId("daily-movement")),
        )
        var externalPublishes = 0

        navigationMutations.runAtomically {
            persistThenPublish(
                persistStagedRoute = {
                    NavigationRouteSnapshotCodec.persist(store, route, durable = false)
                },
                publishVisibleRoute = {
                    visibleRoutes.clear()
                    visibleRoutes += route
                    externalPublishes += 1
                },
            )
        }

        assertEquals(route, visibleRoutes)
        assertEquals(1, externalPublishes)
        assertEquals(1, store.bestEffortWrites)
    }

    @Test
    fun mixedNavKeyStackClearsSnapshotInsteadOfProducingAProductRoute() = runTest {
        var clears = 0

        val routes = appDestinationRoutesOrClear(
            stack = listOf<NavKey>(AppDestination.Today, NonProductNavigationKey),
            clearSnapshot = { clears += 1 },
        )

        assertNull(routes)
        assertEquals(1, clears)
    }

    @Test
    fun coldTodayReplayFailureRetainsLaunchGateUntilFullFifoSucceeds() = runTest {
        val coldAdmission = ColdLaunchGateAdmission()
        val externalNavigation = DeferredExternalNavigationCoordinator()
        val routes = mutableListOf<AppDestination>(AppDestination.LaunchGate)
        val replayed = mutableListOf<ExternalNavigationEvent>()
        val firstDeferred = ExternalNavigationEvent(1, "habitlab://experiment/daily-movement")
        val secondDeferred = ExternalNavigationEvent(2, "habitlab://experiment/sleep-routine")
        val durableStore = AndroidLikeFailingNavigationRouteStore(durableFailuresRemaining = 1)
        var secondReplayFailures = 0
        var routeMutations = 0

        externalNavigation.deferOrProcess(firstDeferred) { replayed += it }
        externalNavigation.deferOrProcess(secondDeferred) { replayed += it }

        suspend fun resolveToday(): Boolean = coldAdmission.resolve(
            prepareResolution = {
                externalNavigation.admitToday(
                    prepareToday = {
                        // The initial route is only persisted while the existing gate is intact.
                        assertEquals(listOf<AppDestination>(AppDestination.LaunchGate), routes)
                        NavigationRouteSnapshotCodec.persist(
                            durableStore,
                            listOf(AppDestination.Today),
                            durable = true,
                        )
                    },
                    replayDeferredEvent = { event ->
                        replayed += event
                        NavigationRouteSnapshotCodec.persist(
                            durableStore,
                            listOf(AppDestination.Today),
                            durable = true,
                        )
                        if (event == secondDeferred && secondReplayFailures++ == 0) {
                            error("controlled second replay failure")
                        }
                    },
                    commitToday = {
                        routes.clear()
                        routes += AppDestination.Today
                        routeMutations += 1
                    },
                )
            },
            restoreLaunchGate = {},
        )

        assertTrue(coldAdmission.mayResolve(isLaunchGateCurrent = true))
        assertFalse(resolveToday())
        assertEquals(listOf<AppDestination>(AppDestination.LaunchGate), routes)
        assertEquals(emptyList(), replayed)
        assertEquals(0, routeMutations)
        assertEquals(1, durableStore.durableWrites)
        assertTrue(coldAdmission.mayResolve(isLaunchGateCurrent = true))

        assertFalse(resolveToday())
        assertEquals(listOf<AppDestination>(AppDestination.LaunchGate), routes)
        assertEquals(listOf(firstDeferred, secondDeferred), replayed)
        assertEquals(0, routeMutations)
        assertEquals(4, durableStore.durableWrites)

        assertTrue(resolveToday())
        assertEquals(listOf<AppDestination>(AppDestination.Today), routes)
        assertEquals(1, routeMutations)
        assertEquals(7, durableStore.durableWrites)
        assertEquals(
            listOf(firstDeferred, secondDeferred, firstDeferred, secondDeferred),
            replayed,
        )
    }

    @Test
    fun boundedPostOnboardingRetryStopsAfterPersistentCommitFailureWithoutAdmittingFifo() = runTest {
        val externalNavigation = DeferredExternalNavigationCoordinator()
        val routes = mutableListOf<AppDestination>(AppDestination.Welcome)
        val replayed = mutableListOf<ExternalNavigationEvent>()
        val deferred = ExternalNavigationEvent(1, "habitlab://experiment/daily-movement")
        var commitAttempts = 0
        var rollbacks = 0
        val retryPolicy = RecordingRetryPolicy(maximumAttempts = 2)

        externalNavigation.deferOrProcess(deferred) { replayed += it }

        val admitted = retryTodayAdmission(retryPolicy) {
            externalNavigation.admitTodayOrRollback(
                prepareToday = {
                    commitAttempts += 1
                    error("controlled persistent snapshot commit failure")
                },
                rollback = {
                    rollbacks += 1
                },
                replayDeferredEvent = { replayed += it },
                commitToday = {
                    routes.clear()
                    routes += AppDestination.Today
                },
            )
        }

        assertFalse(admitted)
        assertEquals(2, commitAttempts)
        assertEquals(2, rollbacks)
        assertEquals(listOf(1), retryPolicy.failedAttempts)
        assertEquals(listOf<AppDestination>(AppDestination.Welcome), routes)
        assertEquals(emptyList(), replayed)
    }

    private class RecordingRetryPolicy(
        override val maximumAttempts: Int,
    ) : TodayAdmissionRetryPolicy {
        val failedAttempts = mutableListOf<Int>()

        override suspend fun awaitRetryAfterFailure(failedAttempt: Int) {
            failedAttempts += failedAttempt
        }
    }

    private class AndroidLikeFailingNavigationRouteStore(
        private var durableFailuresRemaining: Int = 0,
    ) : NavigationRouteSnapshotStore {
        var bestEffortWrites = 0
        var bestEffortClears = 0
        var durableWrites = 0

        override fun read(): String? = null

        override suspend fun write(encodedSnapshot: String) {
            bestEffortWrites += 1
            error("controlled best-effort snapshot failure")
        }

        override suspend fun clear() {
            bestEffortClears += 1
            error("controlled best-effort snapshot clear failure")
        }

        override suspend fun writeDurably(encodedSnapshot: String) {
            durableWrites += 1
            if (durableFailuresRemaining > 0) {
                durableFailuresRemaining -= 1
                error("controlled durable snapshot failure")
            }
        }

        override suspend fun clearDurably() {
            if (durableFailuresRemaining > 0) {
                durableFailuresRemaining -= 1
                error("controlled durable snapshot clear failure")
            }
        }
    }

    private data object NonProductNavigationKey : NavKey
}
