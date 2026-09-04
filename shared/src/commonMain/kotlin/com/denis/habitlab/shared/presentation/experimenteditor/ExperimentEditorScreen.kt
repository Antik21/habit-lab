package com.denis.habitlab.shared.presentation.experimenteditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.presentation.experimenteditor.sections.ExperimentEditorFormSection
import com.denis.habitlab.shared.presentation.experimenteditor.sections.ExperimentEditorMetricSection
import com.denis.habitlab.shared.presentation.experimenteditor.sections.ExperimentEditorSubmitSection
import com.denis.habitlab.shared.presentation.navigation.MetricPickerResult
import com.denis.habitlab.shared.presentation.navigation.rememberNavigationActionDispatcher
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import com.denis.habitlab.shared.presentation.ui.component.HabitLabAppScaffold
import com.denis.habitlab.shared.presentation.ui.component.HabitLabErrorBlock
import com.denis.habitlab.shared.presentation.ui.component.HabitLabLoadingBlock
import com.denis.habitlab.shared.presentation.ui.component.HabitLabToolbar
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.experiment_editor_create_title
import habitlab.shared.generated.resources.experiment_editor_edit_title
import habitlab.shared.generated.resources.experiment_editor_error_accessibility
import habitlab.shared.generated.resources.experiment_editor_error_message
import habitlab.shared.generated.resources.experiment_editor_error_title
import habitlab.shared.generated.resources.experiment_editor_loading_accessibility
import habitlab.shared.generated.resources.experiment_editor_loading_title
import habitlab.shared.generated.resources.navigation_back_accessibility_label
import habitlab.shared.generated.resources.navigation_back_action_label
import org.jetbrains.compose.resources.stringResource
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun ExperimentEditorScreen(
    viewModel: ExperimentEditorViewModel,
    deliveredMetricResult: MetricPickerResult?,
    onMetricResultConsumed: () -> Unit,
    isNavigationActionAllowed: () -> Boolean,
    handleNavigationAction: suspend (NavigationEffect) -> Unit,
) {
    val state by viewModel.collectAsState()
    LaunchedEffect(deliveredMetricResult) {
        deliveredMetricResult?.let {
            viewModel.dispatchAction(Action.MetricResultDelivered(it))
            onMetricResultConsumed()
        }
    }
    viewModel.collectSideEffect { effect ->
        when (effect) { is NavigationEffect -> handleNavigationAction(effect) }
    }
    Content(
        state = state,
        onAction = rememberNavigationActionDispatcher(
            isNavigationActionAllowed = isNavigationActionAllowed,
            dispatchAction = viewModel::dispatchAction,
        ),
    )
}

@Composable
private fun Content(state: ViewState, onAction: (Action) -> Unit) {
    HabitLabAppScaffold(
        automationId = AutomationId.ExperimentEditorScreenRoot,
        toolbar = {
            HabitLabToolbar(
                title = stringResource(
                    if (state.experimentId == null) Res.string.experiment_editor_create_title
                    else Res.string.experiment_editor_edit_title,
                ),
                backActionLabel = stringResource(Res.string.navigation_back_action_label),
                backActionContentDescription = stringResource(Res.string.navigation_back_accessibility_label),
                backAutomationId = AutomationId.ExperimentEditorBack,
                onBack = { onAction(Action.BackClicked) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(HabitLabSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Medium),
        ) {
            when (state.content) {
                ContentUiModel.Loading -> HabitLabLoadingBlock(
                    stringResource(Res.string.experiment_editor_loading_title),
                    stringResource(Res.string.experiment_editor_loading_accessibility),
                    AutomationId.ExperimentEditorLoading,
                )
                ContentUiModel.Error -> HabitLabErrorBlock(
                    stringResource(Res.string.experiment_editor_error_title),
                    stringResource(Res.string.experiment_editor_error_message),
                    stringResource(Res.string.experiment_editor_error_accessibility),
                    AutomationId.ExperimentEditorError,
                )
                ContentUiModel.Ready -> {
                    ExperimentEditorFormSection(
                        state.name, state.validationError, !state.isSaving,
                        onNameChanged = { onAction(Action.NameChanged(it)) },
                    )
                    ExperimentEditorMetricSection(state.metric, !state.isSaving) {
                        onAction(Action.MetricClicked)
                    }
                    ExperimentEditorSubmitSection(
                        state.isSaving, state.validationError, state.commandError,
                        onSave = { onAction(Action.SaveClicked) },
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun Preview() { HabitLabTheme { Content(ViewState(null), {}) } }
