package com.denis.habitlab.shared.domain.observer

import com.denis.habitlab.shared.core.navigation.ExperimentId
import kotlinx.coroutines.flow.Flow

/**
 * Read-side boundary for destination content. Navigation passes an ID; entries always observe a
 * fresh projection instead of restoring a serialized screen model.
 */
interface ExperimentProjectionObserver {
    fun observe(experimentId: ExperimentId): Flow<ExperimentProjection?>
}

data class ExperimentProjection(
    val id: ExperimentId,
    val displayName: String,
)
