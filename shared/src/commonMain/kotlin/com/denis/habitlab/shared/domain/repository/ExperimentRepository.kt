package com.denis.habitlab.shared.domain.repository

import com.denis.habitlab.shared.domain.model.DailyCheckIn
import com.denis.habitlab.shared.domain.model.Experiment
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.model.ExperimentName

/** Write-only repository boundary. Read models are exposed by focused observer contracts. */
interface ExperimentRepository {
    suspend fun createDraft(draft: Experiment): CreateDraftResult

    suspend fun editDraft(
        experimentId: ExperimentId,
        name: ExperimentName,
        updatedAt: com.denis.habitlab.shared.domain.model.RecordedAt,
    ): EditDraftResult

    suspend fun recordDailyCheckIn(checkIn: DailyCheckIn): RecordDailyCheckInResult
}

enum class StorageOperation {
    CREATE_DRAFT,
    EDIT_DRAFT,
    RECORD_DAILY_CHECK_IN,
    OBSERVE_EXPERIMENTS,
    OBSERVE_EXPERIMENT,
    OBSERVE_DAILY_CHECK_IN,
    DEBUG_SEED,
    DEBUG_RESET_AND_SEED,
}

data class StorageFailure(
    val operation: StorageOperation,
)

sealed interface CreateDraftResult {
    data class Created(val draft: Experiment) : CreateDraftResult

    data class Failed(val failure: StorageFailure) : CreateDraftResult
}

sealed interface EditDraftResult {
    data class Updated(val experimentId: ExperimentId) : EditDraftResult

    data class Missing(val experimentId: ExperimentId) : EditDraftResult

    data class NotDraft(val experimentId: ExperimentId) : EditDraftResult

    data class Failed(val failure: StorageFailure) : EditDraftResult
}

sealed interface RecordDailyCheckInResult {
    data class Recorded(val checkIn: DailyCheckIn) : RecordDailyCheckInResult

    data class Missing(val experimentId: ExperimentId) : RecordDailyCheckInResult

    data class Failed(val failure: StorageFailure) : RecordDailyCheckInResult
}
