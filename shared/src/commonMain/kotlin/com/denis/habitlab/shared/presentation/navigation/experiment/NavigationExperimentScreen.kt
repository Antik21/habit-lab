package com.denis.habitlab.shared.presentation.navigation.experiment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.denis.habitlab.shared.presentation.navigation.ExperimentDialogResult
import com.denis.habitlab.shared.presentation.navigation.rememberNavigationActionDispatcher
import com.denis.habitlab.shared.presentation.ui.automation.NavigationSpikeAutomationIds
import com.denis.habitlab.shared.presentation.ui.automation.autodevId
import com.denis.habitlab.shared.presentation.ui.component.HabitLabAppScaffold
import com.denis.habitlab.shared.presentation.ui.component.HabitLabErrorBlock
import com.denis.habitlab.shared.presentation.ui.component.HabitLabLoadingBlock
import com.denis.habitlab.shared.presentation.ui.component.HabitLabPrimaryButton
import com.denis.habitlab.shared.presentation.ui.component.HabitLabSecondaryButton
import com.denis.habitlab.shared.presentation.ui.component.HabitLabToolbar
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.navigation_back_accessibility_label
import habitlab.shared.generated.resources.navigation_back_action_label
import habitlab.shared.generated.resources.navigation_dialog_result_cancelled
import habitlab.shared.generated.resources.navigation_dialog_result_confirmed
import habitlab.shared.generated.resources.navigation_experiment_open_dialog_label
import habitlab.shared.generated.resources.navigation_experiment_open_settings_label
import habitlab.shared.generated.resources.navigation_experiment_error_accessibility_label
import habitlab.shared.generated.resources.navigation_experiment_error_message
import habitlab.shared.generated.resources.navigation_experiment_error_title
import habitlab.shared.generated.resources.navigation_experiment_loading_accessibility_label
import habitlab.shared.generated.resources.navigation_experiment_loading_title
import habitlab.shared.generated.resources.navigation_experiment_start_flow_label
import habitlab.shared.generated.resources.navigation_experiment_subtitle
import habitlab.shared.generated.resources.navigation_experiment_title
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun NavigationExperimentScreen(
    viewModel: NavigationExperimentViewModel,
    deliveredDialogResult: ExperimentDialogResult?,
    dialogResult: NavigationDialogResultDisplay?,
    isNavigationActionAllowed: () -> Boolean,
    onDialogResultConsumed: () -> Unit,
    openApplicationSettings: () -> Unit,
    handleNavigationAction: suspend (NavigationEffect) -> Unit,
) {
    val state by viewModel.collectAsState()

    LaunchedEffect(deliveredDialogResult) {
        deliveredDialogResult?.let { result ->
            viewModel.dispatchAction(Action.DialogResultDelivered(result))
            onDialogResultConsumed()
        }
    }
    viewModel.collectSideEffect { effect ->
        when (effect) {
            is NavigationEffect -> handleNavigationAction(effect)
            ViewEffect.OpenApplicationSettings -> openApplicationSettings()
        }
    }

    Content(
        state = state,
        dialogResult = dialogResult,
        onAction = rememberNavigationActionDispatcher(
            isNavigationActionAllowed = isNavigationActionAllowed,
            dispatchAction = viewModel::dispatchAction,
        ),
    )
}

@Composable
private fun Content(
    state: ViewState,
    dialogResult: NavigationDialogResultDisplay?,
    onAction: (Action) -> Unit,
) {
    HabitLabAppScaffold(
        automationId = NavigationSpikeAutomationIds.experimentScreenRoot,
        toolbar = {
            HabitLabToolbar(
                title = stringResource(Res.string.navigation_experiment_title, state.experimentId),
                backActionLabel = stringResource(Res.string.navigation_back_action_label),
                backActionContentDescription = stringResource(
                    Res.string.navigation_back_accessibility_label,
                ),
                backAutomationId = NavigationSpikeAutomationIds.experimentToolbarBack,
                onBack = { onAction(Action.BackClicked) },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(HabitLabSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Medium),
        ) {
            when (val content = state.content) {
                ContentUiModel.Loading -> HabitLabLoadingBlock(
                    title = stringResource(Res.string.navigation_experiment_loading_title),
                    accessibilityLabel = stringResource(
                        Res.string.navigation_experiment_loading_accessibility_label,
                    ),
                    automationId = NavigationSpikeAutomationIds.experimentLoadingState,
                )

                ContentUiModel.Error -> HabitLabErrorBlock(
                    title = stringResource(Res.string.navigation_experiment_error_title),
                    message = stringResource(Res.string.navigation_experiment_error_message),
                    accessibilityLabel = stringResource(
                        Res.string.navigation_experiment_error_accessibility_label,
                    ),
                    automationId = NavigationSpikeAutomationIds.experimentErrorState,
                )

                is ContentUiModel.Available -> {
                    Text(
                        text = content.displayName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(
                            Res.string.navigation_experiment_subtitle,
                            state.experimentId,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    HabitLabPrimaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(Res.string.navigation_experiment_open_dialog_label),
                        automationId = NavigationSpikeAutomationIds.experimentOpenDialog,
                        onClick = { onAction(Action.OpenDialogClicked) },
                    )
                    HabitLabSecondaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(Res.string.navigation_experiment_start_flow_label),
                        automationId = NavigationSpikeAutomationIds.experimentStartFlow,
                        onClick = { onAction(Action.StartFlowClicked) },
                    )
                    HabitLabSecondaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(Res.string.navigation_experiment_open_settings_label),
                        automationId = NavigationSpikeAutomationIds.experimentOpenSettings,
                        onClick = { onAction(Action.OpenSettingsClicked) },
                    )
                    dialogResult?.let { result ->
                        Text(
                            modifier = Modifier.autodevId(result.automationId),
                            text = result.label(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationDialogResultDisplay.label(): String = when (this) {
    NavigationDialogResultDisplay.Confirmed -> stringResource(
        Res.string.navigation_dialog_result_confirmed,
    )
    NavigationDialogResultDisplay.Cancelled -> stringResource(
        Res.string.navigation_dialog_result_cancelled,
    )
}

@Preview
@Composable
private fun Preview() {
    HabitLabTheme {
        Content(
            state = ViewState(
                experimentId = "daily-movement",
                content = ContentUiModel.Available(displayName = "Daily movement"),
            ),
            dialogResult = NavigationDialogResultDisplay.Confirmed,
            onAction = {},
        )
    }
}
