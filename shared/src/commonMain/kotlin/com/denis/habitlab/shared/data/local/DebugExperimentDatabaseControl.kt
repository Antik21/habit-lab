package com.denis.habitlab.shared.data.local

import com.denis.habitlab.shared.domain.repository.StorageFailure
import com.denis.habitlab.shared.domain.repository.StorageOperation
import kotlinx.coroutines.CancellationException

/**
 * Created only by debug host bootstrap. The automation fixture source writes experiments and the
 * completed onboarding proof in one Room transaction; this control is absent from release graphs.
 */
class DebugExperimentDatabaseControl internal constructor(
    private val localDataSource: RoomExperimentLocalDataSource,
    private val automationFixtureDataSource: DebugAutomationFixtureLocalDataSource? = null,
    private val onSuccessfulReset: () -> Unit = {},
) {
    suspend fun resetAndSeed(): DebugDatabaseResetResult = try {
        automationFixtureDataSource?.resetAndSeed() ?: localDataSource.resetAndSeed()
        onSuccessfulReset()
        DebugDatabaseResetResult.Reset
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        DebugDatabaseResetResult.Failed(StorageFailure(StorageOperation.DEBUG_RESET_AND_SEED))
    }

    internal suspend fun seedIfEmpty(): DebugDatabaseSeedResult = try {
        val seeded = automationFixtureDataSource?.seedIfNoExperiments() ?: localDataSource.seedIfEmpty()
        if (seeded) DebugDatabaseSeedResult.Seeded else DebugDatabaseSeedResult.ExistingData
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
