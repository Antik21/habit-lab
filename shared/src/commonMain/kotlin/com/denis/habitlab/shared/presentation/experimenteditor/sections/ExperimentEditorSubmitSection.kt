package com.denis.habitlab.shared.presentation.experimenteditor.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import com.denis.habitlab.shared.presentation.ui.automation.autodevId
import com.denis.habitlab.shared.presentation.ui.component.HabitLabPrimaryButton
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.experiment_editor_error_message
import habitlab.shared.generated.resources.experiment_editor_save
import habitlab.shared.generated.resources.experiment_editor_validation
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExperimentEditorSubmitSection(
    isSaving: Boolean,
    validationError: Boolean,
    commandError: Boolean,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Small)) {
        if (validationError) Text(
            modifier = Modifier.autodevId(AutomationId.ExperimentEditorValidation),
            text = stringResource(Res.string.experiment_editor_validation),
        )
        if (commandError) Text(
            modifier = Modifier.autodevId(AutomationId.ExperimentEditorError),
            text = stringResource(Res.string.experiment_editor_error_message),
        )
        HabitLabPrimaryButton(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.experiment_editor_save),
            automationId = AutomationId.ExperimentEditorSave,
            onClick = onSave,
            enabled = !isSaving,
        )
    }
}

@Preview
@Composable
private fun Preview() { HabitLabTheme { ExperimentEditorSubmitSection(false, true, false, {}) } }
