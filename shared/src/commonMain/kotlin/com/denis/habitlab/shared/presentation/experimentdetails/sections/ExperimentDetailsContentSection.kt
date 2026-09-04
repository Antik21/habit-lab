package com.denis.habitlab.shared.presentation.experimentdetails.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.presentation.experimentdetails.ContentUiModel
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import com.denis.habitlab.shared.presentation.ui.component.HabitLabErrorBlock
import com.denis.habitlab.shared.presentation.ui.component.HabitLabLoadingBlock
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.experiment_details_error_accessibility
import habitlab.shared.generated.resources.experiment_details_error_message
import habitlab.shared.generated.resources.experiment_details_error_title
import habitlab.shared.generated.resources.experiment_details_loading_accessibility
import habitlab.shared.generated.resources.experiment_details_loading_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExperimentDetailsContentSection(content: ContentUiModel) {
    when (content) {
        ContentUiModel.Loading -> HabitLabLoadingBlock(
            stringResource(Res.string.experiment_details_loading_title),
            stringResource(Res.string.experiment_details_loading_accessibility),
            AutomationId.ExperimentDetailsLoading,
        )
        ContentUiModel.Error -> HabitLabErrorBlock(
            stringResource(Res.string.experiment_details_error_title),
            stringResource(Res.string.experiment_details_error_message),
            stringResource(Res.string.experiment_details_error_accessibility),
            AutomationId.ExperimentDetailsError,
        )
        is ContentUiModel.Available -> Unit
    }
}

@Preview
@Composable
private fun Preview() { HabitLabTheme { ExperimentDetailsContentSection(ContentUiModel.Loading) } }
