package com.denis.habitlab.shared.presentation.experimentdetails

import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObservation
import com.denis.habitlab.shared.presentation.model.toUiModel

class ExperimentDetailsUiMapper {
    fun map(
        observation: ExperimentProjectionObservation,
        fallbackExperimentId: ExperimentId,
    ): ViewState = ViewState(
        experimentId = fallbackExperimentId,
        content = when (observation) {
            is ExperimentProjectionObservation.Available -> ContentUiModel.Available(
                name = observation.projection.displayName,
                status = observation.projection.status.toUiModel(),
            )
            ExperimentProjectionObservation.Missing -> ContentUiModel.Loading
            is ExperimentProjectionObservation.Failed -> ContentUiModel.Error
        },
    )
}
