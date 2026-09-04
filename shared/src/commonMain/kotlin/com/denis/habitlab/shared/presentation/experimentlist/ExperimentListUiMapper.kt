package com.denis.habitlab.shared.presentation.experimentlist

import com.denis.habitlab.shared.domain.observer.ExperimentListObservation

class ExperimentListUiMapper {
    fun map(observation: ExperimentListObservation): ViewState = ViewState(
        content = when (observation) {
            is ExperimentListObservation.Failed -> ContentUiModel.Error
            is ExperimentListObservation.Available -> observation.experiments
                .map { summary ->
                    ExperimentRowUiModel(
                        id = summary.id,
                        name = summary.name.value,
                        status = summary.status,
                    )
                }
                .takeIf(List<ExperimentRowUiModel>::isNotEmpty)
                ?.let(ContentUiModel::Available)
                ?: ContentUiModel.Empty
        },
    )
}
