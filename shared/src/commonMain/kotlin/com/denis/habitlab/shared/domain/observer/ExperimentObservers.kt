package com.denis.habitlab.shared.domain.observer

import com.denis.habitlab.shared.domain.model.DailyCheckIn
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.model.ExperimentStatus
import com.denis.habitlab.shared.domain.model.ExperimentSummary
import com.denis.habitlab.shared.domain.repository.StorageFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface ExperimentProjectionObserver {
    fun observe(experimentId: ExperimentId): Flow<ExperimentProjectionObservation>
}

data class ExperimentProjection(
    val id: ExperimentId,
    val displayName: String,
    val status: ExperimentStatus,
)

sealed interface ExperimentProjectionObservation {
    data class Available(val projection: ExperimentProjection) : ExperimentProjectionObservation

    data object Missing : ExperimentProjectionObservation

    data class Failed(val failure: StorageFailure) : ExperimentProjectionObservation
}

interface ExperimentListObserver {
    fun observeAll(): Flow<ExperimentListObservation>
}

sealed interface ExperimentListObservation {
    data class Available(val experiments: List<ExperimentSummary>) : ExperimentListObservation

    data class Failed(val failure: StorageFailure) : ExperimentListObservation
}

interface DailyCheckInObserver {
    fun observe(
        experimentId: ExperimentId,
        localDate: LocalDate,
    ): Flow<DailyCheckInObservation>
}

sealed interface DailyCheckInObservation {
    data class Available(val checkIn: DailyCheckIn) : DailyCheckInObservation

    data object Missing : DailyCheckInObservation

    data class Failed(val failure: StorageFailure) : DailyCheckInObservation
}
