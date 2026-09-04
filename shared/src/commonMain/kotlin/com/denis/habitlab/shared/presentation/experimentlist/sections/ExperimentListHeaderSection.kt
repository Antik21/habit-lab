package com.denis.habitlab.shared.presentation.experimentlist.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.presentation.ui.automation.ExperimentListAutomationIds
import com.denis.habitlab.shared.presentation.ui.component.HabitLabPrimaryButton
import com.denis.habitlab.shared.presentation.ui.component.HabitLabSecondaryButton
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.experiment_list_create
import habitlab.shared.generated.resources.experiment_list_settings
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExperimentListHeaderSection(
    onCreate: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Small)) {
        HabitLabPrimaryButton(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.experiment_list_create),
            automationId = ExperimentListAutomationIds.create,
            onClick = onCreate,
        )
        HabitLabSecondaryButton(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.experiment_list_settings),
            automationId = ExperimentListAutomationIds.openSettings,
            onClick = onSettings,
        )
    }
}

@Preview
@Composable
private fun Preview() {
    HabitLabTheme { ExperimentListHeaderSection(onCreate = {}, onSettings = {}) }
}
