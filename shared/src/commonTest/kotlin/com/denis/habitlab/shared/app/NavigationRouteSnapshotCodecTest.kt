package com.denis.habitlab.shared.app

import com.denis.habitlab.shared.presentation.navigation.OnboardingStep
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationRouteSnapshotCodecTest {
    @Test
    fun versionThreeRestoresOnlyAsAPostGateCandidateForEveryCompleteRouteShape() = runBlocking {
        val experimentId = ExperimentId("daily-movement")
        val date = CheckInRouteDate.from(kotlinx.datetime.LocalDate.parse("2026-03-04"))
        val validStacks = listOf(
            listOf(AppDestination.Welcome),
            listOf(AppDestination.Welcome, AppDestination.OnboardingCheckpoint(OnboardingStep.OUTCOME)),
            listOf(
                AppDestination.Welcome,
                AppDestination.OnboardingCheckpoint(OnboardingStep.OUTCOME),
                AppDestination.OnboardingCheckpoint(OnboardingStep.CONTEXT),
            ),
            listOf(
                AppDestination.Welcome,
                AppDestination.OnboardingCheckpoint(OnboardingStep.OUTCOME),
                AppDestination.OnboardingCheckpoint(OnboardingStep.CONTEXT),
                AppDestination.OnboardingCheckpoint(OnboardingStep.PROTOCOLS),
                AppDestination.OnboardingCheckpoint(OnboardingStep.HEALTH_EXPLANATION),
                AppDestination.OnboardingCheckpoint(OnboardingStep.STATUS_COVERAGE),
                AppDestination.OnboardingCheckpoint(OnboardingStep.SETUP),
            ),
            listOf(AppDestination.Today),
            listOf(AppDestination.Today, AppDestination.Experiment(experimentId)),
            listOf(AppDestination.Today, AppDestination.ExperimentEditor(null)),
            listOf(AppDestination.Today, AppDestination.ExperimentEditor(null), AppDestination.MetricPicker(null)),
            listOf(AppDestination.Today, AppDestination.Experiment(experimentId), AppDestination.ExperimentEditor(experimentId)),
            listOf(
                AppDestination.Today,
                AppDestination.Experiment(experimentId),
                AppDestination.ExperimentEditor(experimentId),
                AppDestination.MetricPicker(experimentId),
            ),
            listOf(AppDestination.Today, AppDestination.Experiment(experimentId), AppDestination.DailyCheckIn(experimentId, date)),
            listOf(AppDestination.Today, AppDestination.Experiment(experimentId), AppDestination.ConfirmDelete(experimentId)),
            listOf(AppDestination.Today, AppDestination.Settings),
        )

        validStacks.forEach { routes ->
            val store = MemoryRouteStore()
            NavigationRouteSnapshotCodec.persist(store, routes)

            val encoded = requireNotNull(store.payload)
            assertTrue(encoded.contains("\"version\":3"))
            val restored = NavigationRouteSnapshotCodec.restore(encoded)
            assertEquals(listOf(AppDestination.LaunchGate), restored.routes)
            assertEquals(routes, restored.candidateRoutes)
            assertFalse(restored.shouldClearStoredSnapshot)
        }
    }

    @Test
    fun versionTwoMalformedAndIllegalSnapshotsFallBackToLaunchGateAndRequestSerializedCleanup() = runBlocking {
        val experimentId = ExperimentId("daily-movement")
        val store = MemoryRouteStore()
        NavigationRouteSnapshotCodec.persist(
            store,
            listOf(AppDestination.Today, AppDestination.Experiment(experimentId), AppDestination.ConfirmDelete(experimentId)),
        )
        val currentPayload = requireNotNull(store.payload)

        listOf(
            currentPayload.replace("\"version\":3", "\"version\":2"),
            "not-json",
            currentPayload.replace("ConfirmDelete", "MetricPicker"),
            currentPayload.replace("Today", "LaunchGate"),
        ).forEach { payload ->
            val restored = NavigationRouteSnapshotCodec.restore(payload)
            assertEquals(listOf(AppDestination.LaunchGate), restored.routes)
            assertNull(restored.candidateRoutes)
            assertTrue(restored.shouldClearStoredSnapshot)
        }
    }

    @Test
    fun candidateValidationRejectsIllegalRootsParentsAndTypedArguments() {
        val experimentId = ExperimentId("daily-movement")
        val date = CheckInRouteDate.from(kotlinx.datetime.LocalDate.parse("2026-03-04"))

        listOf(
            listOf(AppDestination.LaunchGate),
            listOf(AppDestination.Welcome, AppDestination.OnboardingCheckpoint(OnboardingStep.WELCOME)),
            listOf(AppDestination.Welcome, AppDestination.OnboardingCheckpoint(OnboardingStep.CONTEXT)),
            listOf(AppDestination.Today, AppDestination.ConfirmDelete(experimentId)),
            listOf(AppDestination.Today, AppDestination.Experiment(experimentId), AppDestination.DailyCheckIn(
                ExperimentId("sleep-routine"),
                date,
            )),
        ).forEach { routes ->
            assertFalse(NavigationRouteSnapshotCodec.isValidCompleteRoute(routes), routes.toString())
        }
    }

    @Test
    fun persistingLaunchGateOrAnInvalidStackClearsInsteadOfStoringIt() = runBlocking {
        val store = MemoryRouteStore(payload = "previous")

        NavigationRouteSnapshotCodec.persist(store, listOf(AppDestination.LaunchGate))

        assertNull(store.payload)
        assertEquals(1, store.clearCount)

        NavigationRouteSnapshotCodec.persist(store, listOf(AppDestination.Today, AppDestination.MetricPicker(null)))

        assertNull(store.payload)
        assertEquals(2, store.clearCount)
    }

    @Test
    fun absentSnapshotStartsOnlyAtTheInMemoryLaunchGate() {
        val restored = NavigationRouteSnapshotCodec.restore(null)

        assertEquals(listOf(AppDestination.LaunchGate), restored.routes)
        assertNull(restored.candidateRoutes)
        assertFalse(restored.shouldClearStoredSnapshot)
    }

    private class MemoryRouteStore(var payload: String? = null) : NavigationRouteSnapshotStore {
        var clearCount: Int = 0

        override fun read(): String? = payload

        override suspend fun write(encodedSnapshot: String) {
            payload = encodedSnapshot
        }

        override suspend fun clear() {
            clearCount += 1
            payload = null
        }
    }
}
