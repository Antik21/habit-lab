package com.denis.habitlab.shared.data.local

import com.denis.habitlab.shared.domain.repository.StorageFailure
import com.denis.habitlab.shared.domain.repository.StorageOperation
import kotlinx.coroutines.CancellationException

/**
 * Created only by debug host bootstrap. This is deliberately not registered in a release graph,
 * so production code has no destructive database operation to invoke.
 */
class DebugExperimentDatabaseControl internal constructor(
    private val localDataSource: RoomExperimentLocalDataSource,
) {
    suspend fun resetAndSeed(): DebugDatabaseResetResult = try {
        localDataSource.resetAndSeed()
        DebugDatabaseResetResult.Reset
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        DebugDatabaseResetResult.Failed(StorageFailure(StorageOperation.DEBUG_RESET_AND_SEED))
    }

    internal suspend fun seedIfEmpty(): DebugDatabaseSeedResult = try {
        if (localDataSource.seedIfEmpty()) DebugDatabaseSeedResult.Seeded else DebugDatabaseSeedResult.ExistingData
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        DebugDatabaseSeedResult.Failed(StorageFailure(StorageOperation.DEBUG_SEED))
    }
}

sealed interface DebugDatabaseResetResult {
    data object Reset : DebugDatabaseResetResult

    data class Failed(val failure: StorageFailure) : DebugDatabaseResetResult
}

internal sealed interface DebugDatabaseSeedResult {
    data object Seeded : DebugDatabaseSeedResult

    data object ExistingData : DebugDatabaseSeedResult

    data class Failed(val failure: StorageFailure) : DebugDatabaseSeedResult
}
