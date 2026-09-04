package com.denis.habitlab.shared.presentation.navigation.experiment

import com.denis.habitlab.shared.domain.observer.ExperimentProjection

class NavigationExperimentUiMapper {
    fun map(projection: ExperimentProjection): ViewState = ViewState(
        experimentId = projection.id.value,
    )
}
