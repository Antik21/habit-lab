package com.denis.habitlab.shared.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
}
