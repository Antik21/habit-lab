package com.denis.habitlab.shared.presentation.experimentdetails.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.presentation.model.ExperimentStatusUiModel
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.experiment_details_status
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExperimentDetailsSummarySection(name: String, status: ExperimentStatusUiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.ExtraSmall)) {
        Text(name, style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(Res.string.experiment_details_status, stringResource(status.labelResource)),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Preview
@Composable
private fun Preview() {
    HabitLabTheme { ExperimentDetailsSummarySection("Sleep routine", ExperimentStatusUiModel.DRAFT) }
}
