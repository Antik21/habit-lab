package com.denis.habitlab.shared.presentation.experimenteditor.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.presentation.navigation.MetricKind
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import com.denis.habitlab.shared.presentation.ui.automation.autodevId
import com.denis.habitlab.shared.presentation.ui.component.HabitLabSecondaryButton
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.experiment_editor_metric
import habitlab.shared.generated.resources.experiment_editor_select_metric
import habitlab.shared.generated.resources.metric_picker_energy
import habitlab.shared.generated.resources.metric_picker_sleep
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExperimentEditorMetricSection(metric: MetricKind?, enabled: Boolean, onClick: () -> Unit) {
    val label = metric?.label() ?: stringResource(Res.string.experiment_editor_select_metric)
    Box(modifier = Modifier.fillMaxWidth().autodevId(metric.automationStateId())) {
        HabitLabSecondaryButton(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.experiment_editor_metric, label),
            automationId = AutomationId.ExperimentEditorMetric,
            onClick = onClick,
            enabled = enabled,
        )
    }
}

private fun MetricKind?.automationStateId(): AutomationId = when (this) {
    null -> AutomationId.ExperimentEditorMetricUnset
    MetricKind.DAILY_ENERGY -> AutomationId.ExperimentEditorMetricDailyEnergy
    MetricKind.SLEEP_QUALITY -> AutomationId.ExperimentEditorMetricSleepQuality
}

@Composable
private fun MetricKind.label(): String = when (this) {
    MetricKind.DAILY_ENERGY -> stringResource(Res.string.metric_picker_energy)
    MetricKind.SLEEP_QUALITY -> stringResource(Res.string.metric_picker_sleep)
}

@Preview
@Composable
private fun Preview() { HabitLabTheme { ExperimentEditorMetricSection(MetricKind.DAILY_ENERGY, true, {}) } }
