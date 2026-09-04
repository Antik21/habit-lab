package com.denis.habitlab.shared.presentation.experimentdetails.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.presentation.model.ExperimentStatusUiModel
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import com.denis.habitlab.shared.presentation.ui.component.HabitLabPrimaryButton
import com.denis.habitlab.shared.presentation.ui.component.HabitLabSecondaryButton
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.experiment_details_check_in
import habitlab.shared.generated.resources.experiment_details_delete
import habitlab.shared.generated.resources.experiment_details_edit
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExperimentDetailsActionsSection(
    status: ExperimentStatusUiModel,
    onEdit: () -> Unit,
    onCheckIn: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Small)) {
        HabitLabPrimaryButton(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.experiment_details_check_in),
            automationId = AutomationId.ExperimentDetailsCheckIn,
            onClick = onCheckIn,
        )
        HabitLabSecondaryButton(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.experiment_details_edit),
            automationId = AutomationId.ExperimentDetailsEdit,
            onClick = onEdit,
            enabled = status == ExperimentStatusUiModel.DRAFT,
        )
        HabitLabSecondaryButton(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.experiment_details_delete),
            automationId = AutomationId.ExperimentDetailsDelete,
            onClick = onDelete,
        )
    }
}

@Preview
@Composable
private fun Preview() {
    HabitLabTheme { ExperimentDetailsActionsSection(ExperimentStatusUiModel.DRAFT, {}, {}, {}) }
}
