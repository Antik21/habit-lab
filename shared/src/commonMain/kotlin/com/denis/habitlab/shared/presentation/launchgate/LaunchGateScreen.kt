package com.denis.habitlab.shared.presentation.launchgate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.presentation.ui.automation.LaunchGateAutomationIds
import com.denis.habitlab.shared.presentation.ui.automation.autodevId
import com.denis.habitlab.shared.presentation.ui.component.HabitLabAppScaffold
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.launch_gate_failed_message
import habitlab.shared.generated.resources.launch_gate_invalid_message
import habitlab.shared.generated.resources.launch_gate_retry
import org.jetbrains.compose.resources.stringResource
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/**
 * The normal loading branch intentionally renders no product content: this entry is only a durable
 * launch decision. Invalid and failed observations remain visible and retryable for safe recovery.
 */
@Composable
fun LaunchGateScreen(
    viewModel: LaunchGateViewModel,
    handleNavigationAction: suspend (NavigationEffect) -> Unit,
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { effect ->
        when (effect) {
            is NavigationEffect -> handleNavigationAction(effect)
        }
    }
    Content(state = state, onAction = viewModel::dispatchAction)
}

@Composable
private fun Content(state: ViewState, onAction: (Action) -> Unit) {
    when (state.content) {
        ContentUiModel.Loading -> Box(modifier = Modifier.fillMaxSize().autodevId(LaunchGateAutomationIds.screenRoot)) {
            Box(modifier = Modifier.fillMaxSize().autodevId(LaunchGateAutomationIds.loading))
        }
        ContentUiModel.Invalid -> RecoveryContent(
            message = stringResource(Res.string.launch_gate_invalid_message),
            stateId = LaunchGateAutomationIds.invalid,
            onRetry = { onAction(Action.RetryClicked) },
        )
        ContentUiModel.Failed -> RecoveryContent(
            message = stringResource(Res.string.launch_gate_failed_message),
            stateId = LaunchGateAutomationIds.failed,
            onRetry = { onAction(Action.RetryClicked) },
        )
    }
}

@Composable
private fun RecoveryContent(message: String, stateId: com.denis.habitlab.shared.presentation.ui.automation.AutomationId, onRetry: () -> Unit) {
    HabitLabAppScaffold(automationId = LaunchGateAutomationIds.screenRoot, toolbar = {}) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(HabitLabSpacing.Large).autodevId(stateId),
            verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Medium),
        ) {
            Text(message)
            Button(onClick = onRetry, modifier = Modifier.autodevId(LaunchGateAutomationIds.retry)) {
                Text(stringResource(Res.string.launch_gate_retry))
            }
        }
    }
}

@Preview
@Composable
private fun Preview() {
    HabitLabTheme { Content(ViewState(ContentUiModel.Invalid), onAction = {}) }
}
