package com.denis.habitlab.shared.data.local

import kotlinx.coroutines.flow.Flow

/**
 * Room-only boundary. It carries only persisted data models and primitive values; domain semantics
 * are applied by repositories and observers at the data/domain boundary.
 */
internal class RoomExperimentLocalDataSource(
    database: HabitLabDatabase,
) {
    private val dao = database.experimentDao()

    fun observeExperiments(): Flow<List<ExperimentEntity>> = dao.observeExperiments()

    fun observeExperiment(experimentId: String): Flow<ExperimentEntity?> =
        dao.observeExperiment(experimentId)

    fun observeDailyCheckIn(
        experimentId: String,
        checkInLocalDate: String,
    ): Flow<CheckInEntity?> = dao.observeDailyCheckIn(experimentId, checkInLocalDate)

    suspend fun createDraft(entity: ExperimentEntity) {
        require(entity.status == "DRAFT" && entity.activeSlot == null) {
            "Draft creation accepts only DRAFT entities"
        }
        dao.createDraft(entity)
    }

    suspend fun editDraft(
        experimentId: String,
        displayName: String,
        updatedUtcMillis: Long,
        updatedOffsetSeconds: Int,
        updatedLocalDate: String,
    ): DraftUpdate = dao.editDraft(
        experimentId = experimentId,
        displayName = displayName,
        updatedUtcMillis = updatedUtcMillis,
        updatedOffsetSeconds = updatedOffsetSeconds,
        updatedLocalDate = updatedLocalDate,
    )

    suspend fun recordDailyCheckIn(entity: CheckInEntity): Boolean =
        dao.recordDailyCheckIn(entity)

    suspend fun deleteExperiment(experimentId: String): Boolean = dao.deleteExperimentIfPresent(experimentId)

    suspend fun seedIfEmpty(): Boolean = dao.seedIfEmpty(DebugSeed.fixed)

    suspend fun resetAndSeed() {
        dao.replaceAllWithSeed(DebugSeed.fixed)
    }
}
