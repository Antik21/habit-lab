package com.denis.habitlab.shared.data.observer

import com.denis.habitlab.shared.data.local.DatabaseReadiness
import com.denis.habitlab.shared.data.local.DatabaseReadinessState
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** Maps Room flows to domain observations and turns corrupt/closed DB failures into typed states. */
internal class RoomExperimentObservers(
    private val localDataSource: RoomExperimentLocalDataSource,
    private val databaseReadiness: DatabaseReadiness = DatabaseReadiness(DatabaseReadinessState.Ready),
) : ExperimentProjectionObserver, ExperimentListObserver, DailyCheckInObserver {
    override fun observe(experimentId: ExperimentId): Flow<ExperimentProjectionObservation> =
        afterDatabaseReady { readiness ->
            when (readiness) {
                DatabaseReadinessState.Ready -> {
                    emitAll(
                        localDataSource.observeExperiment(experimentId.value)
                            .map { entity ->
                                entity?.toDomain()?.let { experiment ->
                                    ExperimentProjectionObservation.Available(
                                        ExperimentProjection(
                                            id = experiment.id,
                                            displayName = experiment.name.value,
                                            status = experiment.status,
                                        ),
                                    )
                                } ?: ExperimentProjectionObservation.Missing
                            }
                            .mapRecoverableStorageFailure(StorageOperation.OBSERVE_EXPERIMENT) { failure ->
                                ExperimentProjectionObservation.Failed(failure)
                            },
                    )
                }

                is DatabaseReadinessState.Failed -> {
                    emit(ExperimentProjectionObservation.Failed(readiness.failure))
                }

                DatabaseReadinessState.Initializing -> error("Database readiness must be terminal")
            }
        }

    override fun observeAll(): Flow<ExperimentListObservation> =
        afterDatabaseReady { readiness ->
            when (readiness) {
                DatabaseReadinessState.Ready -> {
                    emitAll(
                        localDataSource.observeExperiments()
                            .map<List<com.denis.habitlab.shared.data.local.ExperimentEntity>, ExperimentListObservation> { entities ->
                                ExperimentListObservation.Available(
                                    entities.map { entity ->
                                        entity.toDomain().let { experiment ->
                                            ExperimentSummary(
                                                id = experiment.id,
                                                name = experiment.name,
                                                status = experiment.status,
                                            )
                                        }
                                    },
                                )
                            }
                            .mapRecoverableStorageFailure(StorageOperation.OBSERVE_EXPERIMENTS) { failure ->
                                ExperimentListObservation.Failed(failure)
                            },
                    )
                }

                is DatabaseReadinessState.Failed -> emit(ExperimentListObservation.Failed(readiness.failure))
                DatabaseReadinessState.Initializing -> error("Database readiness must be terminal")
            }
        }

    override fun observe(
        experimentId: ExperimentId,
        localDate: LocalDate,
    ): Flow<DailyCheckInObservation> = afterDatabaseReady { readiness ->
        when (readiness) {
            DatabaseReadinessState.Ready -> {
                emitAll(
                    localDataSource.observeDailyCheckIn(experimentId.value, localDate.toString())
                        .map { entity ->
                            entity?.toDomain()?.let(DailyCheckInObservation::Available)
                                ?: DailyCheckInObservation.Missing
                        }
                        .mapRecoverableStorageFailure(StorageOperation.OBSERVE_DAILY_CHECK_IN) { failure ->
                            DailyCheckInObservation.Failed(failure)
                        },
                )
            }

            is DatabaseReadinessState.Failed -> emit(DailyCheckInObservation.Failed(readiness.failure))
            DatabaseReadinessState.Initializing -> error("Database readiness must be terminal")
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> afterDatabaseReady(
        observeAfterReady: suspend kotlinx.coroutines.flow.FlowCollector<T>.(DatabaseReadinessState) -> Unit,
    ): Flow<T> = databaseReadiness.state
        .filterNot { it is DatabaseReadinessState.Initializing }
        .flatMapLatest { readiness ->
            flow { observeAfterReady(readiness) }
        }
}

/** Converts only recoverable storage failures; coroutine cancellation and fatal errors stay visible. */
private fun <T> Flow<T>.mapRecoverableStorageFailure(
    operation: StorageOperation,
    failure: (StorageFailure) -> T,
): Flow<T> = catch { throwable ->
    when (throwable) {
        is CancellationException -> throw throwable
        is Error -> throw throwable
        is Exception -> emit(failure(StorageFailure(operation)))
        else -> throw throwable
    }
}
