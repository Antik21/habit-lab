package com.denis.habitlab.shared.presentation.dailycheckin

import androidx.compose.runtime.Immutable
import com.denis.habitlab.shared.domain.model.ExperimentId
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.daily_check_in_existing_performed
import habitlab.shared.generated.resources.daily_check_in_existing_skipped
import org.jetbrains.compose.resources.StringResource

@Immutable
data class ViewState(
    val experimentId: ExperimentId,
    val localDateDisplay: String,
    val content: ContentUiModel = ContentUiModel.Loading,
    val selectedOutcome: CheckInSelectionUiModel = CheckInSelectionUiModel.PERFORMED,
    val hasHydratedInitialSelection: Boolean = false,
    val persistedOutcome: PersistedCheckInStatusUiModel? = null,
    val isSaving: Boolean = false,
    val commandError: Boolean = false,
)

@Immutable
sealed interface ContentUiModel {
    data object Loading : ContentUiModel

    data object Ready : ContentUiModel

    data object ReadError : ContentUiModel
}

@Immutable
enum class CheckInSelectionUiModel {
    PERFORMED,
    SKIPPED,
}

@Immutable
enum class PersistedCheckInStatusUiModel(
    val labelResource: StringResource,
) {
    PERFORMED(Res.string.daily_check_in_existing_performed),
    SKIPPED(Res.string.daily_check_in_existing_skipped),
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
