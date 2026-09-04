package com.denis.habitlab.shared.presentation.dailycheckin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.presentation.dailycheckin.sections.DailyCheckInOutcomeSection
import com.denis.habitlab.shared.presentation.dailycheckin.sections.DailyCheckInSaveSection
import com.denis.habitlab.shared.presentation.dailycheckin.sections.DailyCheckInStatusSection
import com.denis.habitlab.shared.presentation.navigation.rememberNavigationActionDispatcher
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import com.denis.habitlab.shared.presentation.ui.automation.autodevId
import com.denis.habitlab.shared.presentation.ui.component.HabitLabAppScaffold
import com.denis.habitlab.shared.presentation.ui.component.HabitLabErrorBlock
import com.denis.habitlab.shared.presentation.ui.component.HabitLabLoadingBlock
import com.denis.habitlab.shared.presentation.ui.component.HabitLabToolbar
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.daily_check_in_date
import habitlab.shared.generated.resources.daily_check_in_error_accessibility
import habitlab.shared.generated.resources.daily_check_in_error_message
import habitlab.shared.generated.resources.daily_check_in_error_title
import habitlab.shared.generated.resources.daily_check_in_loading_accessibility
import habitlab.shared.generated.resources.daily_check_in_loading_title
import habitlab.shared.generated.resources.daily_check_in_title
import habitlab.shared.generated.resources.navigation_back_accessibility_label
import habitlab.shared.generated.resources.navigation_back_action_label
import org.jetbrains.compose.resources.stringResource
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun DailyCheckInScreen(
    viewModel: DailyCheckInViewModel,
    isNavigationActionAllowed: () -> Boolean,
    handleNavigationAction: suspend (NavigationEffect) -> Unit,
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { effect ->
        when (effect) { is NavigationEffect -> handleNavigationAction(effect) }
    }
    Content(state, rememberNavigationActionDispatcher(isNavigationActionAllowed, dispatchAction = viewModel::dispatchAction))
}

@Composable
private fun Content(state: ViewState, onAction: (Action) -> Unit) {
    HabitLabAppScaffold(
        automationId = AutomationId.DailyCheckInScreenRoot,
        toolbar = {
            HabitLabToolbar(
                title = stringResource(Res.string.daily_check_in_title),
                backActionLabel = stringResource(Res.string.navigation_back_action_label),
                backActionContentDescription = stringResource(Res.string.navigation_back_accessibility_label),
                backAutomationId = AutomationId.DailyCheckInBack,
                onBack = { onAction(Action.BackClicked) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(HabitLabSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Medium),
        ) {
            Text(stringResource(Res.string.daily_check_in_date, state.localDate.toString()))
            when (state.content) {
                ContentUiModel.Loading -> HabitLabLoadingBlock(
                    stringResource(Res.string.daily_check_in_loading_title),
                    stringResource(Res.string.daily_check_in_loading_accessibility),
                    AutomationId.DailyCheckInLoading,
                )
                ContentUiModel.Error -> HabitLabErrorBlock(
                    stringResource(Res.string.daily_check_in_error_title),
                    stringResource(Res.string.daily_check_in_error_message),
                    stringResource(Res.string.daily_check_in_error_accessibility),
                    AutomationId.DailyCheckInError,
                )
                ContentUiModel.Ready -> {
                    if (state.hasExistingCheckIn) DailyCheckInStatusSection(state.selectedIntent)
                    DailyCheckInOutcomeSection(
                        state.selectedIntent, !state.isSaving,
                        onPerformed = { onAction(Action.PerformedClicked) },
                        onSkipped = { onAction(Action.SkippedClicked) },
                    )
                    if (state.commandError) Text(
                        modifier = Modifier.autodevId(AutomationId.DailyCheckInError),
                        text = stringResource(Res.string.daily_check_in_error_message),
                    )
                    DailyCheckInSaveSection(state.isSaving) { onAction(Action.SaveClicked) }
                }
            }
        }
    }
}

@Preview
@Composable
private fun Preview() {
    HabitLabTheme { Content(ViewState(com.denis.habitlab.shared.domain.model.ExperimentId("daily-movement"), kotlinx.datetime.LocalDate(2026, 9, 5)), {}) }
}
