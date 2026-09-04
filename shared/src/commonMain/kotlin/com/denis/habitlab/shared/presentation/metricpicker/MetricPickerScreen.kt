package com.denis.habitlab.shared.presentation.metricpicker

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
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.presentation.navigation.MetricKind
import com.denis.habitlab.shared.presentation.navigation.rememberNavigationActionDispatcher
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import com.denis.habitlab.shared.presentation.ui.automation.autodevId
import com.denis.habitlab.shared.presentation.ui.component.HabitLabPrimaryButton
import com.denis.habitlab.shared.presentation.ui.component.HabitLabSecondaryButton
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.metric_picker_cancel
import habitlab.shared.generated.resources.metric_picker_energy
import habitlab.shared.generated.resources.metric_picker_sleep
import habitlab.shared.generated.resources.metric_picker_title
import org.jetbrains.compose.resources.stringResource
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun MetricPickerScreen(
    viewModel: MetricPickerViewModel,
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
    Surface(modifier = Modifier.autodevId(AutomationId.MetricPickerScreenRoot), shape = MaterialTheme.shapes.extraLarge) {
        Column(
            modifier = Modifier.padding(HabitLabSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Medium),
        ) {
            Text(stringResource(Res.string.metric_picker_title), style = MaterialTheme.typography.titleLarge)
            HabitLabPrimaryButton(
                modifier = Modifier.fillMaxWidth(), label = stringResource(Res.string.metric_picker_energy),
                automationId = AutomationId.MetricPickerEnergy,
                onClick = { onAction(Action.MetricClicked(MetricKind.DAILY_ENERGY)) },
            )
            HabitLabPrimaryButton(
                modifier = Modifier.fillMaxWidth(), label = stringResource(Res.string.metric_picker_sleep),
                automationId = AutomationId.MetricPickerSleep,
                onClick = { onAction(Action.MetricClicked(MetricKind.SLEEP_QUALITY)) },
            )
            HabitLabSecondaryButton(
                modifier = Modifier.fillMaxWidth(), label = stringResource(Res.string.metric_picker_cancel),
                automationId = AutomationId.MetricPickerCancel,
                onClick = { onAction(Action.CancelClicked) },
            )
        }
    }
}

@Preview
@Composable
private fun Preview() { HabitLabTheme { Content(ViewState(null), {}) } }
