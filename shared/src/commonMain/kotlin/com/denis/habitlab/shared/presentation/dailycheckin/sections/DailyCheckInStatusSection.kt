package com.denis.habitlab.shared.presentation.dailycheckin.sections

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.domain.interactor.DailyCheckInIntent
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.daily_check_in_existing_performed
import habitlab.shared.generated.resources.daily_check_in_existing_skipped
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DailyCheckInStatusSection(selected: DailyCheckInIntent) {
    Text(
        text = stringResource(
            if (selected == DailyCheckInIntent.PERFORMED) Res.string.daily_check_in_existing_performed
            else Res.string.daily_check_in_existing_skipped,
        ),
    )
}

@Preview
@Composable
private fun Preview() { HabitLabTheme { DailyCheckInStatusSection(DailyCheckInIntent.SKIPPED) } }
