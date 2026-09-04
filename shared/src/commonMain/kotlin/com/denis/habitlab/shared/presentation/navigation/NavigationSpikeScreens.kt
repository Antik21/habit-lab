package com.denis.habitlab.shared.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import com.denis.habitlab.shared.presentation.ui.automation.NavigationSpikeAutomationIds
import com.denis.habitlab.shared.presentation.ui.automation.autodevId
import com.denis.habitlab.shared.presentation.ui.component.HabitLabAppScaffold
import com.denis.habitlab.shared.presentation.ui.component.HabitLabErrorBlock
import com.denis.habitlab.shared.presentation.ui.component.HabitLabLoadingBlock
import com.denis.habitlab.shared.presentation.ui.component.HabitLabPrimaryButton
import com.denis.habitlab.shared.presentation.ui.component.HabitLabSecondaryButton
import com.denis.habitlab.shared.presentation.ui.component.HabitLabToolbar
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.navigation_back_accessibility_label
import habitlab.shared.generated.resources.navigation_back_action_label
import habitlab.shared.generated.resources.navigation_dialog_cancel_label
import habitlab.shared.generated.resources.navigation_dialog_confirm_label
import habitlab.shared.generated.resources.navigation_dialog_message
import habitlab.shared.generated.resources.navigation_dialog_result_cancelled
import habitlab.shared.generated.resources.navigation_dialog_result_confirmed
import habitlab.shared.generated.resources.navigation_dialog_title
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
import habitlab.shared.generated.resources.navigation_flow_finish_label
import habitlab.shared.generated.resources.navigation_flow_next_label
import habitlab.shared.generated.resources.navigation_flow_step_one_subtitle
import habitlab.shared.generated.resources.navigation_flow_step_one_title
import habitlab.shared.generated.resources.navigation_flow_step_two_subtitle
import habitlab.shared.generated.resources.navigation_flow_step_two_title
import org.jetbrains.compose.resources.stringResource

/** A UI projection of an app-owned typed dialog result. */
enum class NavigationDialogResultDisplay(
    val automationId: AutomationId,
) {
    Confirmed(NavigationSpikeAutomationIds.dialogResultConfirmed),
    Cancelled(NavigationSpikeAutomationIds.dialogResultCancelled),
}

@Composable
fun NavigationDialogResultDisplay.label(): String = when (this) {
    NavigationDialogResultDisplay.Confirmed -> stringResource(Res.string.navigation_dialog_result_confirmed)
    NavigationDialogResultDisplay.Cancelled -> stringResource(Res.string.navigation_dialog_result_cancelled)
}

@Composable
fun NavigationExperimentScreen(
    experimentId: String,
    content: ExperimentContentState,
    dialogResult: NavigationDialogResultDisplay?,
    onBack: () -> Unit,
    onOpenDialog: () -> Unit,
    onStartFlow: () -> Unit,
    onOpenApplicationSettings: () -> Unit,
) {
    HabitLabAppScaffold(
        automationId = NavigationSpikeAutomationIds.experimentScreenRoot,
        toolbar = {
            HabitLabToolbar(
                title = stringResource(Res.string.navigation_experiment_title, experimentId),
                backActionLabel = stringResource(Res.string.navigation_back_action_label),
                backActionContentDescription = stringResource(
                    Res.string.navigation_back_accessibility_label,
                ),
                backAutomationId = NavigationSpikeAutomationIds.experimentToolbarBack,
                onBack = onBack,
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
            when (content) {
                ExperimentContentState.Loading -> HabitLabLoadingBlock(
                    title = stringResource(Res.string.navigation_experiment_loading_title),
                    accessibilityLabel = stringResource(
                        Res.string.navigation_experiment_loading_accessibility_label,
                    ),
                    automationId = NavigationSpikeAutomationIds.experimentLoadingState,
                )

                is ExperimentContentState.Failed -> HabitLabErrorBlock(
                    title = stringResource(Res.string.navigation_experiment_error_title),
                    message = stringResource(Res.string.navigation_experiment_error_message),
                    accessibilityLabel = stringResource(
                        Res.string.navigation_experiment_error_accessibility_label,
                    ),
                    automationId = NavigationSpikeAutomationIds.experimentErrorState,
                )

                is ExperimentContentState.Available -> {
                    Text(
                        text = content.projection.displayName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(Res.string.navigation_experiment_subtitle, experimentId),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    HabitLabPrimaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(Res.string.navigation_experiment_open_dialog_label),
                        automationId = NavigationSpikeAutomationIds.experimentOpenDialog,
                        onClick = onOpenDialog,
                    )
                    HabitLabSecondaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(Res.string.navigation_experiment_start_flow_label),
                        automationId = NavigationSpikeAutomationIds.experimentStartFlow,
                        onClick = onStartFlow,
                    )
                    HabitLabSecondaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(Res.string.navigation_experiment_open_settings_label),
                        automationId = NavigationSpikeAutomationIds.experimentOpenSettings,
                        onClick = onOpenApplicationSettings,
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
fun NavigationFlowStepOneScreen(
    flowId: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    NavigationFlowScreen(
        screenAutomationId = NavigationSpikeAutomationIds.flowStepOneScreenRoot,
        backAutomationId = NavigationSpikeAutomationIds.flowStepOneToolbarBack,
        title = stringResource(Res.string.navigation_flow_step_one_title, flowId),
        subtitle = stringResource(Res.string.navigation_flow_step_one_subtitle),
        actionLabel = stringResource(Res.string.navigation_flow_next_label),
        actionAutomationId = NavigationSpikeAutomationIds.flowStepOneNext,
        onBack = onBack,
        onAction = onNext,
    )
}

@Composable
fun NavigationFlowStepTwoScreen(
    flowId: String,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    NavigationFlowScreen(
        screenAutomationId = NavigationSpikeAutomationIds.flowStepTwoScreenRoot,
        backAutomationId = NavigationSpikeAutomationIds.flowStepTwoToolbarBack,
        title = stringResource(Res.string.navigation_flow_step_two_title, flowId),
        subtitle = stringResource(Res.string.navigation_flow_step_two_subtitle),
        actionLabel = stringResource(Res.string.navigation_flow_finish_label),
        actionAutomationId = NavigationSpikeAutomationIds.flowStepTwoFinish,
        onBack = onBack,
        onAction = onFinish,
    )
}

/** Dialog content; Nav3's DialogSceneStrategy owns the actual overlay window. */
@Composable
fun NavigationExperimentDialogContent(
    experimentId: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier.autodevId(NavigationSpikeAutomationIds.dialogScreenRoot),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(HabitLabSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Medium),
        ) {
            Text(
                text = stringResource(Res.string.navigation_dialog_title, experimentId),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(Res.string.navigation_dialog_message),
                style = MaterialTheme.typography.bodyLarge,
            )
            HabitLabPrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(Res.string.navigation_dialog_confirm_label),
                automationId = NavigationSpikeAutomationIds.dialogConfirm,
                onClick = onConfirm,
            )
            HabitLabSecondaryButton(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(Res.string.navigation_dialog_cancel_label),
                automationId = NavigationSpikeAutomationIds.dialogCancel,
                onClick = onCancel,
            )
        }
    }
}

@Composable
private fun NavigationFlowScreen(
    screenAutomationId: AutomationId,
    backAutomationId: AutomationId,
    title: String,
    subtitle: String,
    actionLabel: String,
    actionAutomationId: AutomationId,
    onBack: () -> Unit,
    onAction: () -> Unit,
) {
    HabitLabAppScaffold(
        automationId = screenAutomationId,
        toolbar = {
            HabitLabToolbar(
                title = title,
                backActionLabel = stringResource(Res.string.navigation_back_action_label),
                backActionContentDescription = stringResource(
                    Res.string.navigation_back_accessibility_label,
                ),
                backAutomationId = backAutomationId,
                onBack = onBack,
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
            Text(text = subtitle, style = MaterialTheme.typography.bodyLarge)
            HabitLabPrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                label = actionLabel,
                automationId = actionAutomationId,
                onClick = onAction,
            )
        }
    }
}
