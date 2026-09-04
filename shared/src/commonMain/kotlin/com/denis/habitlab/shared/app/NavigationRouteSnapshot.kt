package com.denis.habitlab.shared.app

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Platform capability for the small common route snapshot; it never receives screen ViewState. */
internal interface NavigationRouteSnapshotStore {
    fun read(): String?

    suspend fun write(encodedSnapshot: String)

    suspend fun clear()
}

@Composable
internal expect fun rememberNavigationRouteSnapshotStore(): NavigationRouteSnapshotStore

@Serializable
private data class NavigationRouteSnapshot(
    val version: Int,
    val routes: List<AppDestination>,
)

/**
 * Explicitly versioned route-only persistence. Invalid, obsolete, malformed, or structurally
 * impossible data is discarded before Nav3 receives it, leaving a known Gallery root instead.
 */
internal object NavigationRouteSnapshotCodec {
    private const val currentVersion = 1
    private const val maxRouteCount = 12
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        classDiscriminator = "route"
    }

    fun restore(encodedSnapshot: String?): NavigationRouteRestore {
        if (encodedSnapshot == null) return NavigationRouteRestore(routes = root())
        val snapshot = runCatching {
            json.decodeFromString(NavigationRouteSnapshot.serializer(), encodedSnapshot)
        }.getOrNull()
        if (snapshot == null || snapshot.version != currentVersion || !isValid(snapshot.routes)) {
            return NavigationRouteRestore(routes = root(), shouldClearStoredSnapshot = true)
        }
        return NavigationRouteRestore(routes = snapshot.routes)
    }

    suspend fun persist(store: NavigationRouteSnapshotStore, routes: List<AppDestination>) {
        if (!isValid(routes)) {
            store.clear()
            return
        }
        store.write(
            json.encodeToString(
                NavigationRouteSnapshot.serializer(),
                NavigationRouteSnapshot(version = currentVersion, routes = routes),
            ),
        )
    }

    private fun isValid(routes: List<AppDestination>): Boolean {
        if (routes.size !in 1..maxRouteCount || routes.firstOrNull() != AppDestination.Gallery) {
            return false
        }
        var previous = routes.first()
        routes.drop(1).forEach { destination ->
            if (!canFollow(previous, destination)) return false
            previous = destination
        }
        return true
    }

    private fun canFollow(previous: AppDestination, destination: AppDestination): Boolean = when (destination) {
        AppDestination.Gallery -> false
        is AppDestination.Experiment -> {
            previous == AppDestination.Gallery &&
                ExperimentId.fromExternalValue(destination.experimentId.value) != null
        }

        is AppDestination.FlowStepOne -> when (previous) {
            AppDestination.Gallery -> destination.flowId == FlowId.gallerySetup()
            is AppDestination.Experiment -> destination.flowId == FlowId.forExperiment(previous.experimentId)
            else -> false
        }

        is AppDestination.FlowStepTwo -> previous == AppDestination.FlowStepOne(destination.flowId)
        is AppDestination.ConfirmExperiment -> {
            previous == AppDestination.Experiment(destination.experimentId) &&
                ExperimentId.fromExternalValue(destination.experimentId.value) != null
        }
    }

    private fun root(): List<AppDestination> = listOf(AppDestination.Gallery)
}

/** Restore output separates safe in-memory fallback from asynchronous platform invalidation. */
internal data class NavigationRouteRestore(
    val routes: List<AppDestination>,
    val shouldClearStoredSnapshot: Boolean = false,
)
