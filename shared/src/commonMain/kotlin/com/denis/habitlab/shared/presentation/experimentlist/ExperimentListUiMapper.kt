package com.denis.habitlab.shared.presentation.experimentlist

import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.observer.ExperimentListObservation
import com.denis.habitlab.shared.presentation.model.toUiModel
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import kotlinx.collections.immutable.toImmutableList

class ExperimentListUiMapper {
    fun map(observation: ExperimentListObservation): ViewState = ViewState(
        content = when (observation) {
            is ExperimentListObservation.Failed -> ContentUiModel.Error
            is ExperimentListObservation.Available -> observation.experiments
                .map { summary ->
                    ExperimentRowUiModel(
                        id = summary.id,
                        name = summary.name.value,
                        status = summary.status.toUiModel(),
                        automationId = experimentListRowAutomationIdFor(summary.id),
                    )
                }
                .toImmutableList()
                .takeIf { it.isNotEmpty() }
                ?.let(ContentUiModel::Available)
                ?: ContentUiModel.Empty
        },
    )

}

internal fun experimentListRowAutomationIdFor(experimentId: ExperimentId): AutomationId = when (experimentId) {
    dailyMovementExperimentId -> AutomationId.ExperimentListDailyMovementRow
    sleepRoutineExperimentId -> AutomationId.ExperimentListSleepRoutineRow
    else -> AutomationId.ExperimentListRow
}

private val dailyMovementExperimentId = ExperimentId.fromExternalValue("daily-movement")
    ?: error("The closed reference ExperimentId must remain valid.")
private val sleepRoutineExperimentId = ExperimentId.fromExternalValue("sleep-routine")
    ?: error("The closed reference ExperimentId must remain valid.")
