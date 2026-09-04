package com.denis.habitlab.shared.presentation.metricpicker

import androidx.compose.runtime.Immutable
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.presentation.navigation.MetricKind
import com.denis.habitlab.shared.presentation.navigation.MetricPickerResult

@Immutable
data class ViewState(val experimentId: ExperimentId?)

sealed interface Action {
    data class MetricClicked(val metric: MetricKind) : Action
    data object CancelClicked : Action
}

sealed interface SideEffect

sealed interface NavigationEffect : SideEffect { data class Resolve(val result: MetricPickerResult) : NavigationEffect }
