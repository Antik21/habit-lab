package com.denis.habitlab.shared.presentation.dailycheckin

import androidx.compose.runtime.Immutable
import com.denis.habitlab.shared.domain.interactor.DailyCheckInIntent
import com.denis.habitlab.shared.domain.model.ExperimentId
import kotlinx.datetime.LocalDate

@Immutable
data class ViewState(
    val experimentId: ExperimentId,
    val localDate: LocalDate,
    val content: ContentUiModel = ContentUiModel.Loading,
    val selectedIntent: DailyCheckInIntent = DailyCheckInIntent.PERFORMED,
    val hasExistingCheckIn: Boolean = false,
    val isSaving: Boolean = false,
    val commandError: Boolean = false,
)

@Immutable
sealed interface ContentUiModel {
    data object Loading : ContentUiModel
    data object Ready : ContentUiModel
    data object Error : ContentUiModel
}

sealed interface Action {
    data object BackClicked : Action
    data object PerformedClicked : Action
    data object SkippedClicked : Action
    data object SaveClicked : Action
}

sealed interface SideEffect

sealed interface NavigationEffect : SideEffect {
    data object Back : NavigationEffect
    data object PopToRoot : NavigationEffect
}
