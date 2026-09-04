package com.denis.habitlab.shared.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ExperimentDao {
    @Query("SELECT * FROM experiments ORDER BY created_utc_millis ASC, id ASC")
    fun observeExperiments(): Flow<List<ExperimentEntity>>

    @Query("SELECT * FROM experiments WHERE id = :experimentId LIMIT 1")
    fun observeExperiment(experimentId: String): Flow<ExperimentEntity?>

    @Query(
        "SELECT * FROM daily_check_ins " +
            "WHERE experiment_id = :experimentId AND check_in_local_date = :checkInLocalDate LIMIT 1",
    )
    fun observeDailyCheckIn(experimentId: String, checkInLocalDate: String): Flow<CheckInEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExperiment(entity: ExperimentEntity)

    @Query(
        "UPDATE experiments SET display_name = :displayName, updated_utc_millis = :updatedUtcMillis, " +
            "updated_offset_seconds = :updatedOffsetSeconds, updated_local_date = :updatedLocalDate " +
            "WHERE id = :experimentId AND status = 'DRAFT'",
    )
    suspend fun updateDraft(
        experimentId: String,
        displayName: String,
        updatedUtcMillis: Long,
        updatedOffsetSeconds: Int,
        updatedLocalDate: String,
    ): Int

    @Query("SELECT status FROM experiments WHERE id = :experimentId LIMIT 1")
    suspend fun statusFor(experimentId: String): String?

    @Query("SELECT COUNT(*) FROM experiments WHERE id = :experimentId")
    suspend fun experimentCount(experimentId: String): Int

    @Query("DELETE FROM experiments WHERE id = :experimentId")
    suspend fun deleteExperiment(experimentId: String): Int

    @Query("SELECT COUNT(*) FROM experiments")
    suspend fun totalExperimentCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceCheckIn(entity: CheckInEntity)

    @Query("DELETE FROM daily_check_ins")
    suspend fun deleteAllCheckIns()

    @Query("DELETE FROM experiments")
    suspend fun deleteAllExperiments()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExperiments(entities: List<ExperimentEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCheckIns(entities: List<CheckInEntity>)

    @Transaction
    suspend fun createDraft(entity: ExperimentEntity) {
        insertExperiment(entity)
    }

    @Transaction
    suspend fun editDraft(
        experimentId: String,
        displayName: String,
        updatedUtcMillis: Long,
        updatedOffsetSeconds: Int,
        updatedLocalDate: String,
    ): DraftUpdate = when (
        updateDraft(
            experimentId = experimentId,
            displayName = displayName,
            updatedUtcMillis = updatedUtcMillis,
            updatedOffsetSeconds = updatedOffsetSeconds,
            updatedLocalDate = updatedLocalDate,
        )
    ) {
        1 -> DraftUpdate.Updated
        else -> when (statusFor(experimentId)) {
            null -> DraftUpdate.Missing
            else -> DraftUpdate.NotDraft
        }
    }

    @Transaction
    suspend fun recordDailyCheckIn(entity: CheckInEntity): Boolean {
        if (experimentCount(entity.experimentId) == 0) return false
        insertOrReplaceCheckIn(entity)
        return true
    }

    @Transaction
    suspend fun deleteExperimentIfPresent(experimentId: String): Boolean =
        deleteExperiment(experimentId) == 1

    @Transaction
    suspend fun seedIfEmpty(seed: DebugSeed): Boolean {
        if (totalExperimentCount() != 0) return false
        replaceAllWithSeed(seed)
        return true
    }

    @Transaction
    suspend fun replaceAllWithSeed(seed: DebugSeed) {
        deleteAllCheckIns()
        deleteAllExperiments()
        insertExperiments(seed.experiments)
        insertCheckIns(seed.checkIns)
    }
}

internal enum class DraftUpdate {
    Updated,
    Missing,
    NotDraft,
}
