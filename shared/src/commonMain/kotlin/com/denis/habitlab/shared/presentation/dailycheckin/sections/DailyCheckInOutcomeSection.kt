package com.denis.habitlab.shared.presentation.dailycheckin.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.presentation.dailycheckin.CheckInSelectionUiModel
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import com.denis.habitlab.shared.presentation.ui.component.HabitLabPrimaryButton
import com.denis.habitlab.shared.presentation.ui.component.HabitLabSecondaryButton
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.daily_check_in_performed
import habitlab.shared.generated.resources.daily_check_in_skipped
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DailyCheckInOutcomeSection(
    selected: CheckInSelectionUiModel,
    enabled: Boolean,
    onPerformed: () -> Unit,
    onSkipped: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Small)) {
        HabitLabPrimaryButton(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.daily_check_in_performed),
            automationId = AutomationId.DailyCheckInPerformed,
            onClick = onPerformed,
            enabled = enabled && selected != CheckInSelectionUiModel.PERFORMED,
        )
        HabitLabSecondaryButton(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.daily_check_in_skipped),
            automationId = AutomationId.DailyCheckInSkipped,
            onClick = onSkipped,
            enabled = enabled && selected != CheckInSelectionUiModel.SKIPPED,
        )
    }
}

@Preview
@Composable
private fun Preview() { HabitLabTheme { DailyCheckInOutcomeSection(CheckInSelectionUiModel.PERFORMED, true, {}, {}) } }
