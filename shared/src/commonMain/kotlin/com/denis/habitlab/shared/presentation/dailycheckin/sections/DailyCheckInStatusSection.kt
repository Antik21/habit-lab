package com.denis.habitlab.shared.presentation.dailycheckin.sections

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.presentation.dailycheckin.PersistedCheckInStatusUiModel
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DailyCheckInStatusSection(status: PersistedCheckInStatusUiModel) {
    Text(text = stringResource(status.labelResource))
}

@Preview
@Composable
private fun Preview() { HabitLabTheme { DailyCheckInStatusSection(PersistedCheckInStatusUiModel.SKIPPED) } }
