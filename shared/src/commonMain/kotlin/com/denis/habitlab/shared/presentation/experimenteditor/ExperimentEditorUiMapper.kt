package com.denis.habitlab.shared.presentation.experimenteditor

import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObservation

class ExperimentEditorUiMapper {
    fun map(
        observation: ExperimentProjectionObservation,
        experimentId: ExperimentId,
        currentState: ViewState,
    ): ViewState = when (observation) {
        is ExperimentProjectionObservation.Available -> currentState.copy(
            content = ContentUiModel.Ready,
            name = if (currentState.name.isEmpty()) observation.projection.displayName else currentState.name,
        )
        ExperimentProjectionObservation.Missing -> currentState.copy(content = ContentUiModel.Loading)
        is ExperimentProjectionObservation.Failed -> currentState.copy(content = ContentUiModel.Error)
    }
}
