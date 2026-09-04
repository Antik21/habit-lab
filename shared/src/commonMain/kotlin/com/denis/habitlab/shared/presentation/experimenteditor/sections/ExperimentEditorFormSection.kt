package com.denis.habitlab.shared.presentation.experimenteditor.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import com.denis.habitlab.shared.presentation.ui.component.HabitLabTextField
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.experiment_editor_name_accessibility
import habitlab.shared.generated.resources.experiment_editor_name_label
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExperimentEditorFormSection(
    name: String,
    validationError: Boolean,
    enabled: Boolean,
    onNameChanged: (String) -> Unit,
) {
    HabitLabTextField(
        value = name,
        label = stringResource(Res.string.experiment_editor_name_label),
        accessibilityLabel = stringResource(Res.string.experiment_editor_name_accessibility),
        automationId = AutomationId.ExperimentEditorName,
        onValueChange = onNameChanged,
        isError = validationError,
        enabled = enabled,
    )
}

@Preview
@Composable
private fun Preview() { HabitLabTheme { ExperimentEditorFormSection("Sleep", false, true, {}) } }
