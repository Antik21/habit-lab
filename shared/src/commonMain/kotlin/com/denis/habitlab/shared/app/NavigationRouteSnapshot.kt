package com.denis.habitlab.shared.app

import androidx.compose.runtime.Composable
import com.denis.habitlab.shared.presentation.navigation.OnboardingStep
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Platform capability for the small common route snapshot; it never receives screen ViewState. */
internal interface NavigationRouteSnapshotStore {
    fun read(): String?

    /** Best-effort snapshot update for ordinary post-Today navigation. */
    suspend fun write(encodedSnapshot: String)

    /** Best-effort snapshot invalidation for ordinary navigation. */
    suspend fun clear()

    /** Cold gate/admission callers require a confirmed durable outcome. */
    suspend fun writeDurably(encodedSnapshot: String) = write(encodedSnapshot)

    /** Cold gate/admission callers require a confirmed durable outcome. */
    suspend fun clearDurably() = clear()
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
 * impossible data is discarded before Nav3 receives it, leaving an in-memory LaunchGate root.
 */
internal object NavigationRouteSnapshotCodec {
    private const val currentVersion = 3
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
        if (snapshot == null || snapshot.version != currentVersion || !isValidCompleteRoute(snapshot.routes)) {
            return NavigationRouteRestore(routes = root(), shouldClearStoredSnapshot = true)
        }
        // A valid stored stack is only a candidate. Cold launch always starts from LaunchGate and
        // admits this candidate later, after durable onboarding eligibility has been resolved.
        return NavigationRouteRestore(routes = root(), candidateRoutes = snapshot.routes)
    }

    suspend fun persist(
        store: NavigationRouteSnapshotStore,
        routes: List<AppDestination>,
        durable: Boolean = false,
    ) {
        if (!isValidCompleteRoute(routes)) {
            clear(store, durable)
            return
        }
        val encodedSnapshot = json.encodeToString(
            NavigationRouteSnapshot.serializer(),
            NavigationRouteSnapshot(version = currentVersion, routes = routes),
        )
        if (durable) store.writeDurably(encodedSnapshot)
        else bestEffort { store.write(encodedSnapshot) }
    }

    suspend fun clear(store: NavigationRouteSnapshotStore, durable: Boolean) {
        if (durable) store.clearDurably()
        else bestEffort { store.clear() }
    }

    private suspend fun bestEffort(operation: suspend () -> Unit) {
        try {
            operation()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
        }
    }

    internal fun isValidCompleteRoute(routes: List<AppDestination>): Boolean {
        if (routes.size !in 1..maxRouteCount || routes.firstOrNull() !in setOf(AppDestination.Welcome, AppDestination.Today)) {
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
        AppDestination.LaunchGate, AppDestination.Welcome, AppDestination.Today -> false
        is AppDestination.OnboardingCheckpoint -> when (previous) {
            AppDestination.Welcome -> destination.step == OnboardingStep.OUTCOME
            is AppDestination.OnboardingCheckpoint -> destination.step.ordinal == previous.step.ordinal + 1 &&
                destination.step != OnboardingStep.WELCOME
            else -> false
        }
        is AppDestination.Experiment -> {
            previous == AppDestination.Today &&
                ExperimentId.fromInternalValue(destination.experimentId.value) != null
        }
        is AppDestination.ExperimentEditor -> when (previous) {
            AppDestination.Today -> destination.experimentId == null
            is AppDestination.Experiment -> destination.experimentId == previous.experimentId
            else -> false
        }
        is AppDestination.DailyCheckIn -> previous is AppDestination.Experiment &&
            previous.experimentId == destination.experimentId &&
            runCatching { destination.localDate.toLocalDate() }.isSuccess
        AppDestination.Settings -> previous == AppDestination.Today
        is AppDestination.MetricPicker -> previous is AppDestination.ExperimentEditor &&
            previous.experimentId == destination.experimentId
        is AppDestination.ConfirmDelete -> previous is AppDestination.Experiment &&
            previous.experimentId == destination.experimentId
    }

    private fun root(): List<AppDestination> = listOf(AppDestination.LaunchGate)
}

/** Restore output separates safe in-memory fallback from asynchronous platform invalidation. */
internal data class NavigationRouteRestore(
    val routes: List<AppDestination>,
    val candidateRoutes: List<AppDestination>? = null,
    val shouldClearStoredSnapshot: Boolean = false,
)
