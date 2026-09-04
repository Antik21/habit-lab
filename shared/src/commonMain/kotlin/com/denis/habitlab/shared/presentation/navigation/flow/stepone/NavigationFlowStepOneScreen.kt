package com.denis.habitlab.shared.presentation.navigation.flow.stepone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.denis.habitlab.shared.presentation.navigation.rememberNavigationActionDispatcher
import com.denis.habitlab.shared.presentation.ui.automation.NavigationSpikeAutomationIds
import com.denis.habitlab.shared.presentation.ui.component.HabitLabAppScaffold
import com.denis.habitlab.shared.presentation.ui.component.HabitLabPrimaryButton
import com.denis.habitlab.shared.presentation.ui.component.HabitLabToolbar
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.navigation_back_accessibility_label
import habitlab.shared.generated.resources.navigation_back_action_label
import habitlab.shared.generated.resources.navigation_flow_next_label
import habitlab.shared.generated.resources.navigation_flow_step_one_subtitle
import habitlab.shared.generated.resources.navigation_flow_step_one_title
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun NavigationFlowStepOneScreen(
    viewModel: NavigationFlowStepOneViewModel,
    isNavigationActionAllowed: () -> Boolean,
    handleNavigationAction: suspend (NavigationEffect) -> Unit,
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { effect ->
        when (effect) {
            is NavigationEffect -> handleNavigationAction(effect)
        }
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
private fun Content(
    state: ViewState,
    onAction: (Action) -> Unit,
) {
    HabitLabAppScaffold(
        automationId = NavigationSpikeAutomationIds.flowStepOneScreenRoot,
        toolbar = {
            HabitLabToolbar(
                title = stringResource(Res.string.navigation_flow_step_one_title, state.flowId),
                backActionLabel = stringResource(Res.string.navigation_back_action_label),
                backActionContentDescription = stringResource(
                    Res.string.navigation_back_accessibility_label,
                ),
                backAutomationId = NavigationSpikeAutomationIds.flowStepOneToolbarBack,
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
            Text(
                text = stringResource(Res.string.navigation_flow_step_one_subtitle),
                style = MaterialTheme.typography.bodyLarge,
            )
            HabitLabPrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(Res.string.navigation_flow_next_label),
                automationId = NavigationSpikeAutomationIds.flowStepOneNext,
                onClick = { onAction(Action.NextClicked) },
            )
        }
    }
}

@Preview
@Composable
private fun Preview() {
    HabitLabTheme {
        Content(state = ViewState(flowId = "gallery-setup"), onAction = {})
    }
}
