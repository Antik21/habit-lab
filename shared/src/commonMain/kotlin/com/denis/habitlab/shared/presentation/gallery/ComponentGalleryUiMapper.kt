package com.denis.habitlab.shared.presentation.gallery

import com.denis.habitlab.shared.domain.observer.ExperimentListObservation
import com.denis.habitlab.shared.presentation.navigation.ExperimentId

class ComponentGalleryUiMapper {
    fun map(
        observation: ExperimentListObservation,
        habitName: String,
    ): ViewState = ViewState(
        habitName = habitName,
        experiments = mapExperiments(observation),
    )

    private fun mapExperiments(observation: ExperimentListObservation): ExperimentsUiModel =
        when (observation) {
            is ExperimentListObservation.Failed -> ExperimentsUiModel.Error
            is ExperimentListObservation.Available -> {
                val externalIds = observation.experiments
                    .mapNotNull { summary ->
                        summary.id.takeIf { id ->
                            ExperimentId.fromExternalValue(id.value) == id
                        }
                    }
                    .toSet()
                val hasDailyMovement = DAILY_MOVEMENT_ID in externalIds
                val hasSleepRoutine = SLEEP_ROUTINE_ID in externalIds

                if (hasDailyMovement || hasSleepRoutine) {
                    ExperimentsUiModel.Available(
                        hasDailyMovement = hasDailyMovement,
                        hasSleepRoutine = hasSleepRoutine,
                    )
                } else {
                    ExperimentsUiModel.Empty
                }
            }
        }

    private companion object {
        val DAILY_MOVEMENT_ID = requireNotNull(ExperimentId.fromExternalValue("daily-movement"))
        val SLEEP_ROUTINE_ID = requireNotNull(ExperimentId.fromExternalValue("sleep-routine"))
    }
}
