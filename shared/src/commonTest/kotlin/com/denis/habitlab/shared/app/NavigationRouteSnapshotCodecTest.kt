package com.denis.habitlab.shared.app

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationRouteSnapshotCodecTest {
    @Test
    fun versionTwoRestoresEverySupportedReferenceRouteShape() = runBlocking {
        val experimentId = ExperimentId("daily-movement")
        val date = CheckInRouteDate.from(kotlinx.datetime.LocalDate.parse("2026-03-04"))
        val validStacks = listOf(
            listOf(AppDestination.Gallery, AppDestination.Experiment(experimentId)),
            listOf(AppDestination.Gallery, AppDestination.ExperimentEditor(null)),
            listOf(AppDestination.Gallery, AppDestination.ExperimentEditor(null), AppDestination.MetricPicker(null)),
            listOf(AppDestination.Gallery, AppDestination.Experiment(experimentId), AppDestination.ExperimentEditor(experimentId)),
            listOf(
                AppDestination.Gallery,
                AppDestination.Experiment(experimentId),
                AppDestination.ExperimentEditor(experimentId),
                AppDestination.MetricPicker(experimentId),
            ),
            listOf(AppDestination.Gallery, AppDestination.Experiment(experimentId), AppDestination.DailyCheckIn(experimentId, date)),
            listOf(AppDestination.Gallery, AppDestination.Experiment(experimentId), AppDestination.ConfirmDelete(experimentId)),
            listOf(AppDestination.Gallery, AppDestination.Settings),
        )

        validStacks.forEach { routes ->
            val store = MemoryRouteStore()
            NavigationRouteSnapshotCodec.persist(store, routes)

            val encoded = requireNotNull(store.payload)
            assertTrue(encoded.contains("\"version\":2"))
            val restored = NavigationRouteSnapshotCodec.restore(encoded)
            assertEquals(routes, restored.routes)
            assertFalse(restored.shouldClearStoredSnapshot)
        }
    }

    @Test
    fun obsoleteAndStructurallyInvalidSnapshotsFallBackAndRequestCleanup() = runBlocking {
        val experimentId = ExperimentId("daily-movement")
        val store = MemoryRouteStore()
        NavigationRouteSnapshotCodec.persist(
            store,
            listOf(AppDestination.Gallery, AppDestination.Experiment(experimentId), AppDestination.ConfirmDelete(experimentId)),
        )
        val currentPayload = requireNotNull(store.payload)

        val obsolete = NavigationRouteSnapshotCodec.restore(
            currentPayload.replace("\"version\":2", "\"version\":1"),
        )
        assertEquals(listOf(AppDestination.Gallery), obsolete.routes)
        assertTrue(obsolete.shouldClearStoredSnapshot)

        val invalidRoutePair = NavigationRouteSnapshotCodec.restore(
            currentPayload.replace("ConfirmDelete", "MetricPicker"),
        )
        assertEquals(listOf(AppDestination.Gallery), invalidRoutePair.routes)
        assertTrue(invalidRoutePair.shouldClearStoredSnapshot)
    }

    @Test
    fun persistingAnInvalidStackClearsInsteadOfStoringIt() = runBlocking {
        val store = MemoryRouteStore(payload = "previous")

        NavigationRouteSnapshotCodec.persist(store, listOf(AppDestination.Gallery, AppDestination.MetricPicker(null)))

        assertNull(store.payload)
        assertEquals(1, store.clearCount)
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
