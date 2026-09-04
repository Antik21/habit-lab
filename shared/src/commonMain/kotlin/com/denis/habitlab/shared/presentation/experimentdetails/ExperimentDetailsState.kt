package com.denis.habitlab.shared.presentation.experimentdetails

import androidx.compose.runtime.Immutable
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.presentation.model.ExperimentStatusUiModel
import kotlinx.datetime.LocalDate
import com.denis.habitlab.shared.presentation.navigation.DeleteDialogResult

@Immutable
data class ViewState(
    val experimentId: ExperimentId,
    val content: ContentUiModel = ContentUiModel.Loading,
)

@Immutable
sealed interface ContentUiModel {
    data object Loading : ContentUiModel
    data object Error : ContentUiModel
    data class Available(
        val name: String,
        val status: ExperimentStatusUiModel,
    ) : ContentUiModel
}

sealed interface Action {
    data object BackClicked : Action
    data object EditClicked : Action
    data object DailyCheckInClicked : Action
    data object DeleteClicked : Action
    data class DeleteResultDelivered(val result: DeleteDialogResult) : Action
}

sealed interface SideEffect

sealed interface NavigationEffect : SideEffect {
    data object Back : NavigationEffect
    data class OpenEditor(val experimentId: ExperimentId) : NavigationEffect
    data class OpenDailyCheckIn(val experimentId: ExperimentId, val localDate: LocalDate) : NavigationEffect
    data class OpenConfirmDelete(val experimentId: ExperimentId) : NavigationEffect
    data object PopToRoot : NavigationEffect
}
