package com.denis.habitlab.shared.presentation.confirmdelete

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.presentation.navigation.rememberNavigationActionDispatcher
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import com.denis.habitlab.shared.presentation.ui.automation.autodevId
import com.denis.habitlab.shared.presentation.ui.component.HabitLabPrimaryButton
import com.denis.habitlab.shared.presentation.ui.component.HabitLabSecondaryButton
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.confirm_delete_cancel
import habitlab.shared.generated.resources.confirm_delete_confirm
import habitlab.shared.generated.resources.confirm_delete_error
import habitlab.shared.generated.resources.confirm_delete_message
import habitlab.shared.generated.resources.confirm_delete_title
import org.jetbrains.compose.resources.stringResource
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun ConfirmDeleteScreen(
    viewModel: ConfirmDeleteViewModel,
    onDismissalLockChanged: (Boolean) -> Unit,
    isNavigationActionAllowed: () -> Boolean,
    handleNavigationAction: suspend (NavigationEffect) -> Unit,
) {
    val state by viewModel.collectAsState()
    LaunchedEffect(state.isDeleting) { onDismissalLockChanged(state.isDeleting) }
    viewModel.collectSideEffect { effect ->
        when (effect) { is NavigationEffect -> handleNavigationAction(effect) }
    }
    val dispatchAction = rememberNavigationActionDispatcher(
        isNavigationActionAllowed = isNavigationActionAllowed,
        dispatchAction = viewModel::dispatchAction,
    )
    Content(state) { action ->
        if (action == Action.ConfirmClicked && isNavigationActionAllowed()) {
            onDismissalLockChanged(true)
        }
        dispatchAction(action)
    }
}

@Composable
private fun Content(state: ViewState, onAction: (Action) -> Unit) {
    Surface(modifier = Modifier.autodevId(AutomationId.ConfirmDeleteScreenRoot), shape = MaterialTheme.shapes.extraLarge) {
        Column(
            modifier = Modifier.padding(HabitLabSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Medium),
        ) {
            Text(stringResource(Res.string.confirm_delete_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(Res.string.confirm_delete_message), style = MaterialTheme.typography.bodyLarge)
            if (state.commandError) Text(
                modifier = Modifier.autodevId(AutomationId.ConfirmDeleteError),
                text = stringResource(Res.string.confirm_delete_error),
            )
            HabitLabPrimaryButton(
                modifier = Modifier.fillMaxWidth(), label = stringResource(Res.string.confirm_delete_confirm),
                automationId = AutomationId.ConfirmDeleteConfirm,
                onClick = { onAction(Action.ConfirmClicked) }, enabled = !state.isDeleting,
            )
            HabitLabSecondaryButton(
                modifier = Modifier.fillMaxWidth(), label = stringResource(Res.string.confirm_delete_cancel),
                automationId = AutomationId.ConfirmDeleteCancel,
                onClick = { onAction(Action.CancelClicked) }, enabled = !state.isDeleting,
            )
        }
    }
}

@Preview
@Composable
private fun Preview() { HabitLabTheme { Content(ViewState(com.denis.habitlab.shared.domain.model.ExperimentId("daily-movement")), {}) } }
