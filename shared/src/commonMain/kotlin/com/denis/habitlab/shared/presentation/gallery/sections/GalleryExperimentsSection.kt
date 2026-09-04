package com.denis.habitlab.shared.presentation.gallery.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.denis.habitlab.shared.presentation.ui.automation.ComponentGalleryAutomationIds
import com.denis.habitlab.shared.presentation.ui.component.HabitLabClickableListRow
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.gallery_row_daily_movement_accessibility_label
import habitlab.shared.generated.resources.gallery_row_daily_movement_subtitle
import habitlab.shared.generated.resources.gallery_row_daily_movement_title
import habitlab.shared.generated.resources.gallery_row_sleep_routine_accessibility_label
import habitlab.shared.generated.resources.gallery_row_sleep_routine_subtitle
import habitlab.shared.generated.resources.gallery_row_sleep_routine_title
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun GalleryExperimentsSection(onExperimentClicked: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Medium)) {
        HabitLabClickableListRow(
            title = stringResource(Res.string.gallery_row_daily_movement_title),
            supportingText = stringResource(Res.string.gallery_row_daily_movement_subtitle),
            accessibilityLabel = stringResource(
                Res.string.gallery_row_daily_movement_accessibility_label,
            ),
            automationId = ComponentGalleryAutomationIds.firstRow,
            onClick = { onExperimentClicked(DAILY_MOVEMENT_EXPERIMENT_ID) },
        )
        HabitLabClickableListRow(
            title = stringResource(Res.string.gallery_row_sleep_routine_title),
            supportingText = stringResource(Res.string.gallery_row_sleep_routine_subtitle),
            accessibilityLabel = stringResource(
                Res.string.gallery_row_sleep_routine_accessibility_label,
            ),
            automationId = ComponentGalleryAutomationIds.secondRow,
            onClick = { onExperimentClicked(SLEEP_ROUTINE_EXPERIMENT_ID) },
        )
    }
}

@Preview
@Composable
private fun Preview() {
    HabitLabTheme { GalleryExperimentsSection(onExperimentClicked = {}) }
}

private const val DAILY_MOVEMENT_EXPERIMENT_ID = "daily-movement"
private const val SLEEP_ROUTINE_EXPERIMENT_ID = "sleep-routine"
