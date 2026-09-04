package com.denis.habitlab.shared.presentation.experimentlist.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.presentation.experimentlist.ContentUiModel
import com.denis.habitlab.shared.presentation.experimentlist.ExperimentRowUiModel
import com.denis.habitlab.shared.presentation.model.ExperimentStatusUiModel
import com.denis.habitlab.shared.presentation.ui.automation.ExperimentListAutomationIds
import com.denis.habitlab.shared.presentation.ui.component.HabitLabClickableListRow
import com.denis.habitlab.shared.presentation.ui.component.HabitLabEmptyBlock
import com.denis.habitlab.shared.presentation.ui.component.HabitLabErrorBlock
import com.denis.habitlab.shared.presentation.ui.component.HabitLabLoadingBlock
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.experiment_list_empty_accessibility
import habitlab.shared.generated.resources.experiment_list_empty_message
import habitlab.shared.generated.resources.experiment_list_empty_title
import habitlab.shared.generated.resources.experiment_list_error_accessibility
import habitlab.shared.generated.resources.experiment_list_error_message
import habitlab.shared.generated.resources.experiment_list_error_title
import habitlab.shared.generated.resources.experiment_list_loading_accessibility
import habitlab.shared.generated.resources.experiment_list_loading_title
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExperimentListContentSection(
    content: ContentUiModel,
    onExperimentClicked: (ExperimentId) -> Unit,
) {
    when (content) {
        ContentUiModel.Loading -> HabitLabLoadingBlock(
            title = stringResource(Res.string.experiment_list_loading_title),
            accessibilityLabel = stringResource(Res.string.experiment_list_loading_accessibility),
            automationId = ExperimentListAutomationIds.loading,
        )
        ContentUiModel.Empty -> HabitLabEmptyBlock(
            title = stringResource(Res.string.experiment_list_empty_title),
            message = stringResource(Res.string.experiment_list_empty_message),
            accessibilityLabel = stringResource(Res.string.experiment_list_empty_accessibility),
            automationId = ExperimentListAutomationIds.empty,
        )
        ContentUiModel.Error -> HabitLabErrorBlock(
            title = stringResource(Res.string.experiment_list_error_title),
            message = stringResource(Res.string.experiment_list_error_message),
            accessibilityLabel = stringResource(Res.string.experiment_list_error_accessibility),
            automationId = ExperimentListAutomationIds.error,
        )
        is ContentUiModel.Available -> Column(verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Small)) {
            content.experiments.forEach { experiment ->
                HabitLabClickableListRow(
                    title = experiment.name,
                    supportingText = stringResource(experiment.status.labelResource),
                    accessibilityLabel = experiment.name,
                    automationId = ExperimentListAutomationIds.row,
                    onClick = { onExperimentClicked(experiment.id) },
                )
            }
        }
    }
}

@Preview
@Composable
private fun Preview() {
    HabitLabTheme {
        ExperimentListContentSection(
            content = ContentUiModel.Available(
                persistentListOf(
                    ExperimentRowUiModel(
                        ExperimentId("daily-movement"),
                        "Daily movement",
                        ExperimentStatusUiModel.ACTIVE,
                    ),
                ),
            ),
            onExperimentClicked = {},
        )
    }
}
