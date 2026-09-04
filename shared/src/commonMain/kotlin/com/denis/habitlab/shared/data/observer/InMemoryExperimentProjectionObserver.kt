package com.denis.habitlab.shared.data.observer

import com.denis.habitlab.shared.core.navigation.ExperimentId
import com.denis.habitlab.shared.domain.observer.ExperimentProjection
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Replaceable DEN-10 scaffold read model. It deliberately implements the domain observer rather
 * than leaking a data source into an entry ViewModel; DEN-11 can replace this binding with its
 * persisted projection without changing navigation contracts.
 */
class InMemoryExperimentProjectionObserver : ExperimentProjectionObserver {
    private val projections = MutableStateFlow(
        listOf(
            ExperimentProjection(ExperimentId("daily-movement"), "Daily movement"),
            ExperimentProjection(ExperimentId("sleep-routine"), "Sleep routine"),
        ),
    )

    override fun observe(experimentId: ExperimentId): Flow<ExperimentProjection?> =
        projections.map { current -> current.firstOrNull { it.id == experimentId } }
}
