package com.denis.habitlab.shared.presentation.experimentdetails.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.domain.model.ExperimentStatus
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.experiment_details_status
import habitlab.shared.generated.resources.experiment_status_active
import habitlab.shared.generated.resources.experiment_status_draft
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExperimentDetailsSummarySection(name: String, status: ExperimentStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.ExtraSmall)) {
        Text(name, style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(Res.string.experiment_details_status, status.label()),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ExperimentStatus.label(): String = when (this) {
    ExperimentStatus.DRAFT -> stringResource(Res.string.experiment_status_draft)
    ExperimentStatus.ACTIVE -> stringResource(Res.string.experiment_status_active)
}

@Preview
@Composable
private fun Preview() {
    HabitLabTheme { ExperimentDetailsSummarySection("Sleep routine", ExperimentStatus.DRAFT) }
}
