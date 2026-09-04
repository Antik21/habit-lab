package com.denis.habitlab.shared.presentation.experimenteditor

import androidx.compose.runtime.Immutable
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.presentation.navigation.MetricKind
import com.denis.habitlab.shared.presentation.navigation.MetricPickerResult

@Immutable
data class ViewState(
    val experimentId: ExperimentId?,
    val content: ContentUiModel = ContentUiModel.Ready,
    val name: String = "",
    val metric: MetricKind? = null,
    val isSaving: Boolean = false,
    val validationError: Boolean = false,
    val commandError: Boolean = false,
)

@Immutable
sealed interface ContentUiModel {
    data object Ready : ContentUiModel
    data object Loading : ContentUiModel
    data object Error : ContentUiModel
}

sealed interface Action {
    data object BackClicked : Action
    data class NameChanged(val value: String) : Action
    data object MetricClicked : Action
    data class MetricResultDelivered(val result: MetricPickerResult) : Action
    data object SaveClicked : Action
}

sealed interface SideEffect

sealed interface NavigationEffect : SideEffect {
    data object Back : NavigationEffect
    data class OpenMetricPicker(val experimentId: ExperimentId?) : NavigationEffect
    data class SaveComplete(val experimentId: ExperimentId) : NavigationEffect
    data object PopToRoot : NavigationEffect
}
