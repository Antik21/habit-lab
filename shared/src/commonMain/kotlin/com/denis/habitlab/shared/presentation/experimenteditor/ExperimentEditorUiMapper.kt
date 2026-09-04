package com.denis.habitlab.shared.presentation.experimenteditor

import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObservation

class ExperimentEditorUiMapper {
    fun map(
        observation: ExperimentProjectionObservation,
        currentState: ViewState,
    ): ViewState = when (observation) {
        is ExperimentProjectionObservation.Available -> currentState.copy(
            content = ContentUiModel.Ready,
            name = if (currentState.hasHydratedInitialName) {
                currentState.name
            } else {
                observation.projection.displayName
            },
            hasHydratedInitialName = true,
        )
        ExperimentProjectionObservation.Missing -> currentState.copy(content = ContentUiModel.Loading)
        is ExperimentProjectionObservation.Failed -> currentState.copy(content = ContentUiModel.ReadError)
    }
}
