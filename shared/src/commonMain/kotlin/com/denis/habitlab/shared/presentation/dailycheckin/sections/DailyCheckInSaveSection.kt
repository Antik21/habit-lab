package com.denis.habitlab.shared.presentation.dailycheckin.sections

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import com.denis.habitlab.shared.presentation.ui.component.HabitLabPrimaryButton
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.daily_check_in_save
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DailyCheckInSaveSection(isSaving: Boolean, onSave: () -> Unit) {
    HabitLabPrimaryButton(
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(Res.string.daily_check_in_save),
        automationId = AutomationId.DailyCheckInSave,
        onClick = onSave,
        enabled = !isSaving,
    )
}

@Preview
@Composable
private fun Preview() { HabitLabTheme { DailyCheckInSaveSection(false, {}) } }
