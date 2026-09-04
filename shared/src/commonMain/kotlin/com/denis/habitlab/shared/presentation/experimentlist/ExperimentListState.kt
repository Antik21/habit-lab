package com.denis.habitlab.shared.presentation.experimentlist

import androidx.compose.runtime.Immutable
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.presentation.model.ExperimentStatusUiModel
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class ViewState(
    val content: ContentUiModel = ContentUiModel.Loading,
)

@Immutable
sealed interface ContentUiModel {
    data object Loading : ContentUiModel
    data object Empty : ContentUiModel
    data object Error : ContentUiModel
    data class Available(val experiments: ImmutableList<ExperimentRowUiModel>) : ContentUiModel
}

@Immutable
data class ExperimentRowUiModel(
    val id: ExperimentId,
    val name: String,
    val status: ExperimentStatusUiModel,
)

sealed interface Action {
    data object CreateClicked : Action
    data object SettingsClicked : Action
    data class ExperimentClicked(val experimentId: ExperimentId) : Action
}

sealed interface SideEffect

sealed interface NavigationEffect : SideEffect {
    data class OpenDetails(val experimentId: ExperimentId) : NavigationEffect
    data object OpenCreateEditor : NavigationEffect
    data object OpenSettings : NavigationEffect
}
