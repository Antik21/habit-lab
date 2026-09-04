package com.denis.habitlab.shared.presentation.navigation.experiment

import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObservation
import com.denis.habitlab.shared.presentation.navigation.ExperimentId

class NavigationExperimentUiMapper {
    fun map(
        observation: ExperimentProjectionObservation,
        fallbackExperimentId: ExperimentId,
    ): ViewState = when (observation) {
        is ExperimentProjectionObservation.Available -> ViewState(
            experimentId = observation.projection.id.value,
            content = ContentUiModel.Available(
                displayName = observation.projection.displayName,
            ),
        )
        ExperimentProjectionObservation.Missing -> ViewState(
            experimentId = fallbackExperimentId.value,
            content = ContentUiModel.Loading,
        )
        is ExperimentProjectionObservation.Failed -> ViewState(
            experimentId = fallbackExperimentId.value,
            content = ContentUiModel.Error,
        )
    }
}
