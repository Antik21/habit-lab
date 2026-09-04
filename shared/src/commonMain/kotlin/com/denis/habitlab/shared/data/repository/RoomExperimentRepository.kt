package com.denis.habitlab.shared.data.repository

import com.denis.habitlab.shared.data.local.DraftUpdate
import com.denis.habitlab.shared.data.local.RoomExperimentLocalDataSource
import com.denis.habitlab.shared.data.mapper.toEntity
import com.denis.habitlab.shared.domain.model.DailyCheckIn
import com.denis.habitlab.shared.domain.model.Experiment
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.model.ExperimentName
import com.denis.habitlab.shared.domain.model.RecordedAt
import com.denis.habitlab.shared.domain.repository.CreateDraftResult
import com.denis.habitlab.shared.domain.repository.EditDraftResult
import com.denis.habitlab.shared.domain.repository.ExperimentRepository
import com.denis.habitlab.shared.domain.repository.RecordDailyCheckInResult
import com.denis.habitlab.shared.domain.repository.StorageFailure
import com.denis.habitlab.shared.domain.repository.StorageOperation
import kotlinx.coroutines.CancellationException

internal class RoomExperimentRepository(
    private val localDataSource: RoomExperimentLocalDataSource,
) : ExperimentRepository {
    override suspend fun createDraft(draft: Experiment): CreateDraftResult =
        storageWrite(StorageOperation.CREATE_DRAFT) {
            localDataSource.createDraft(draft.toEntity())
            CreateDraftResult.Created(draft)
        }.getOrElse { failure -> CreateDraftResult.Failed(failure) }

    override suspend fun editDraft(
        experimentId: ExperimentId,
        name: ExperimentName,
        updatedAt: RecordedAt,
    ): EditDraftResult = storageWrite(StorageOperation.EDIT_DRAFT) {
        when (localDataSource.editDraft(
            experimentId = experimentId.value,
            displayName = name.value,
            updatedUtcMillis = updatedAt.utcInstant.toEpochMilliseconds(),
            updatedOffsetSeconds = updatedAt.originalOffset.totalSeconds,
            updatedLocalDate = updatedAt.localDate.toString(),
        )) {
            DraftUpdate.Updated -> EditDraftResult.Updated(experimentId)
            DraftUpdate.Missing -> EditDraftResult.Missing(experimentId)
            DraftUpdate.NotDraft -> EditDraftResult.NotDraft(experimentId)
        }
    }.getOrElse { failure -> EditDraftResult.Failed(failure) }

    override suspend fun recordDailyCheckIn(checkIn: DailyCheckIn): RecordDailyCheckInResult =
        storageWrite(StorageOperation.RECORD_DAILY_CHECK_IN) {
            if (localDataSource.recordDailyCheckIn(checkIn.toEntity())) {
                RecordDailyCheckInResult.Recorded(checkIn)
            } else {
                RecordDailyCheckInResult.Missing(checkIn.experimentId)
            }
        }.getOrElse { failure -> RecordDailyCheckInResult.Failed(failure) }
}

private suspend fun <T> storageWrite(
    operation: StorageOperation,
    block: suspend () -> T,
): Result<T, StorageFailure> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    Result.failure(StorageFailure(operation))
}

/** Small common result holder to avoid leaking the platform exception as a domain error. */
private sealed interface Result<out T, out E> {
    data class Success<T>(val value: T) : Result<T, Nothing>

    data class Failure<E>(val error: E) : Result<Nothing, E>

    companion object {
        fun <T> success(value: T): Result<T, Nothing> = Success(value)

        fun <E> failure(error: E): Result<Nothing, E> = Failure(error)
    }
}

private inline fun <T, E, R> Result<T, E>.getOrElse(onFailure: (E) -> R): R where T : R = when (this) {
    is Result.Success -> value
    is Result.Failure -> onFailure(error)
}
