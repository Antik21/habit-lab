package com.denis.habitlab.shared.presentation.navigation.confirmation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.denis.habitlab.shared.presentation.ui.automation.NavigationSpikeAutomationIds
import com.denis.habitlab.shared.presentation.ui.automation.autodevId
import com.denis.habitlab.shared.presentation.ui.component.HabitLabPrimaryButton
import com.denis.habitlab.shared.presentation.ui.component.HabitLabSecondaryButton
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.navigation_dialog_cancel_label
import habitlab.shared.generated.resources.navigation_dialog_confirm_label
import habitlab.shared.generated.resources.navigation_dialog_message
import habitlab.shared.generated.resources.navigation_dialog_title
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun NavigationConfirmationDialogScreen(
    viewModel: NavigationConfirmationDialogViewModel,
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
private fun Content(
    state: ViewState,
    onAction: (Action) -> Unit,
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
                text = stringResource(Res.string.navigation_dialog_title, state.experimentId),
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
                onClick = { onAction(Action.ConfirmClicked) },
            )
            HabitLabSecondaryButton(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(Res.string.navigation_dialog_cancel_label),
                automationId = NavigationSpikeAutomationIds.dialogCancel,
                onClick = { onAction(Action.CancelClicked) },
            )
        }
    }
}

@Preview
@Composable
private fun Preview() {
    HabitLabTheme {
        Content(state = ViewState(experimentId = "daily-movement"), onAction = {})
    }
}
