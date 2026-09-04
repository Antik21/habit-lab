package com.denis.habitlab.shared.data.local

import com.denis.habitlab.shared.domain.repository.StorageFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Host-bootstrap gate for database observers.
 *
 * Debug startup seeds asynchronously, so Room must not be observed as an empty user database until
 * the seed transaction has either completed or produced a typed failure. Release starts ready.
 */
internal sealed interface DatabaseReadinessState {
    data object Initializing : DatabaseReadinessState

    data object Ready : DatabaseReadinessState

    data class Failed(val failure: StorageFailure) : DatabaseReadinessState
}

internal class DatabaseReadiness(initialState: DatabaseReadinessState) {
    private val mutableState = MutableStateFlow(initialState)

    val state: StateFlow<DatabaseReadinessState> = mutableState

    fun markReady() {
        mutableState.value = DatabaseReadinessState.Ready
    }

    fun markFailed(failure: StorageFailure) {
        mutableState.value = DatabaseReadinessState.Failed(failure)
    }
}

/** Runs the debug-only first-use seed and completes the observer gate exactly once per runtime. */
internal class DebugDatabaseBootstrap(
    private val debugDatabaseControl: DebugExperimentDatabaseControl,
    private val databaseReadiness: DatabaseReadiness,
) {
    suspend fun initialize() {
        when (val result = debugDatabaseControl.seedIfEmpty()) {
            DebugDatabaseSeedResult.Seeded, DebugDatabaseSeedResult.ExistingData -> databaseReadiness.markReady()

            is DebugDatabaseSeedResult.Failed -> databaseReadiness.markFailed(result.failure)
        }
    }
}
