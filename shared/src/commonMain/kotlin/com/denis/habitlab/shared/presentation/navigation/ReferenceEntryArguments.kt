package com.denis.habitlab.shared.presentation.navigation

import com.denis.habitlab.shared.domain.model.ExperimentId

/** Non-null Koin entry parameters preserve nullable create-route IDs without sentinel values. */
data class ExperimentEditorEntryArguments(val experimentId: ExperimentId?)

data class MetricPickerEntryArguments(val experimentId: ExperimentId?)
