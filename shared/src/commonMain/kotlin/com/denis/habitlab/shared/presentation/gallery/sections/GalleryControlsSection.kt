package com.denis.habitlab.shared.presentation.gallery.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.denis.habitlab.shared.presentation.ui.automation.ComponentGalleryAutomationIds
import com.denis.habitlab.shared.presentation.ui.component.HabitLabPrimaryButton
import com.denis.habitlab.shared.presentation.ui.component.HabitLabSecondaryButton
import com.denis.habitlab.shared.presentation.ui.component.HabitLabTextField
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.gallery_field_accessibility_label
import habitlab.shared.generated.resources.gallery_field_label
import habitlab.shared.generated.resources.gallery_primary_action_label
import habitlab.shared.generated.resources.gallery_secondary_action_label
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun GalleryControlsSection(
    habitName: String,
    onOpenDialog: () -> Unit,
    onStartFlow: () -> Unit,
    onHabitNameChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Medium)) {
        HabitLabPrimaryButton(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.gallery_primary_action_label),
            automationId = ComponentGalleryAutomationIds.primaryAction,
            onClick = onOpenDialog,
        )
        HabitLabSecondaryButton(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.gallery_secondary_action_label),
            automationId = ComponentGalleryAutomationIds.secondaryAction,
            onClick = onStartFlow,
        )
        HabitLabTextField(
            value = habitName,
            label = stringResource(Res.string.gallery_field_label),
            accessibilityLabel = stringResource(Res.string.gallery_field_accessibility_label),
            automationId = ComponentGalleryAutomationIds.textField,
            onValueChange = onHabitNameChanged,
        )
    }
}

@Preview
@Composable
private fun Preview() {
    HabitLabTheme {
        GalleryControlsSection(
            habitName = "Morning walk",
            onOpenDialog = {},
            onStartFlow = {},
            onHabitNameChanged = {},
        )
    }
}
