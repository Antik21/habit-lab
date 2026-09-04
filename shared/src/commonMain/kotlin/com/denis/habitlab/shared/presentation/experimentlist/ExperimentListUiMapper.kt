package com.denis.habitlab.shared.presentation.experimentlist

import com.denis.habitlab.shared.domain.observer.ExperimentListObservation
import com.denis.habitlab.shared.presentation.model.toUiModel
import kotlinx.collections.immutable.toImmutableList

class ExperimentListUiMapper {
    fun map(observation: ExperimentListObservation): ViewState = ViewState(
        content = when (observation) {
            is ExperimentListObservation.Failed -> ContentUiModel.Error
            is ExperimentListObservation.Available -> observation.experiments
                .map { summary ->
                    ExperimentRowUiModel(
                        id = summary.id,
                        name = summary.name.value,
                        status = summary.status.toUiModel(),
                    )
                }
                .toImmutableList()
                .takeIf { it.isNotEmpty() }
                ?.let(ContentUiModel::Available)
                ?: ContentUiModel.Empty
        },
    )
}
