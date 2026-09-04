package com.denis.habitlab.shared.data.observer

import com.denis.habitlab.shared.data.local.RoomExperimentLocalDataSource
import com.denis.habitlab.shared.data.mapper.toDomain
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.model.ExperimentSummary
import com.denis.habitlab.shared.domain.observer.DailyCheckInObservation
import com.denis.habitlab.shared.domain.observer.DailyCheckInObserver
import com.denis.habitlab.shared.domain.observer.ExperimentListObservation
import com.denis.habitlab.shared.domain.observer.ExperimentListObserver
import com.denis.habitlab.shared.domain.observer.ExperimentProjection
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObservation
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObserver
import com.denis.habitlab.shared.domain.repository.StorageFailure
import com.denis.habitlab.shared.domain.repository.StorageOperation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** Maps Room flows to domain observations and turns corrupt/closed DB failures into typed states. */
internal class RoomExperimentObservers(
    private val localDataSource: RoomExperimentLocalDataSource,
) : ExperimentProjectionObserver, ExperimentListObserver, DailyCheckInObserver {
    override fun observe(experimentId: ExperimentId): Flow<ExperimentProjectionObservation> =
        localDataSource.observeExperiment(experimentId.value)
            .map { entity ->
                entity?.toDomain()?.let { draft ->
                    ExperimentProjectionObservation.Available(
                        ExperimentProjection(
                            id = draft.id,
                            displayName = draft.name.value,
                            status = draft.status,
                        ),
                    )
                } ?: ExperimentProjectionObservation.Missing
            }
            .catch {
                emit(ExperimentProjectionObservation.Failed(StorageFailure(StorageOperation.OBSERVE_EXPERIMENT)))
            }

    override fun observeAll(): Flow<ExperimentListObservation> =
        localDataSource.observeExperiments()
            .map<List<com.denis.habitlab.shared.data.local.ExperimentEntity>, ExperimentListObservation> { entities ->
                ExperimentListObservation.Available(
                    entities.map { entity ->
                        entity.toDomain().let { draft ->
                            ExperimentSummary(
                                id = draft.id,
                                name = draft.name,
                                status = draft.status,
                            )
                        }
                    },
                )
            }
            .catch {
                emit(ExperimentListObservation.Failed(StorageFailure(StorageOperation.OBSERVE_EXPERIMENTS)))
            }

    override fun observe(
        experimentId: ExperimentId,
        localDate: LocalDate,
    ): Flow<DailyCheckInObservation> = localDataSource.observeDailyCheckIn(experimentId.value, localDate.toString())
        .map { entity ->
            entity?.toDomain()?.let(DailyCheckInObservation::Available)
                ?: DailyCheckInObservation.Missing
        }
        .catch {
            emit(DailyCheckInObservation.Failed(StorageFailure(StorageOperation.OBSERVE_DAILY_CHECK_IN)))
        }
}
