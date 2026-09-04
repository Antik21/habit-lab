package com.denis.habitlab.shared.presentation.metricpicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.presentation.navigation.MetricPickerResult
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

class MetricPickerViewModel(
    private val experimentId: ExperimentId?,
) : ViewModel(), ContainerHost<ViewState, SideEffect> {
    override val container: Container<ViewState, SideEffect> = viewModelScope.container(ViewState(experimentId))
    private var hasResolved = false

    fun dispatchAction(action: Action) {
        when (action) {
            is Action.MetricClicked -> resolve(MetricPickerResult.Selected(experimentId, action.metric))
            Action.CancelClicked -> resolve(MetricPickerResult.Cancelled(experimentId))
        }
    }

    private fun resolve(result: MetricPickerResult) = intent {
        if (!hasResolved) {
            hasResolved = true
            postSideEffect(NavigationEffect.Resolve(result))
        }
    }
}
