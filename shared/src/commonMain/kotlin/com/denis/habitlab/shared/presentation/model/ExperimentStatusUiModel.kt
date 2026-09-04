package com.denis.habitlab.shared.presentation.model

import androidx.compose.runtime.Immutable
import com.denis.habitlab.shared.domain.model.ExperimentStatus
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.experiment_status_active
import habitlab.shared.generated.resources.experiment_status_draft
import org.jetbrains.compose.resources.StringResource

@Immutable
enum class ExperimentStatusUiModel(
    val labelResource: StringResource,
) {
    DRAFT(Res.string.experiment_status_draft),
    ACTIVE(Res.string.experiment_status_active),
}

fun ExperimentStatus.toUiModel(): ExperimentStatusUiModel = when (this) {
    ExperimentStatus.DRAFT -> ExperimentStatusUiModel.DRAFT
    ExperimentStatus.ACTIVE -> ExperimentStatusUiModel.ACTIVE
}
