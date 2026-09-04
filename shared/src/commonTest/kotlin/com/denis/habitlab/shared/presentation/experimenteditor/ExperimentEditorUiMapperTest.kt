package com.denis.habitlab.shared.presentation.experimenteditor

import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.model.ExperimentStatus
import com.denis.habitlab.shared.domain.observer.ExperimentProjection
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObservation
import com.denis.habitlab.shared.domain.repository.StorageFailure
import com.denis.habitlab.shared.domain.repository.StorageOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExperimentEditorUiMapperTest {
    private val experimentId = ExperimentId("daily-movement")
    private val mapper = ExperimentEditorUiMapper()

    @Test
    fun persistedNameHydratesOnceAndDoesNotOverwriteAClearedField() {
        val initialState = ViewState(experimentId = experimentId, content = ContentUiModel.Loading)

        val hydrated = mapper.map(available(name = "Persisted name"), initialState)
        val userCleared = hydrated.copy(name = "")
        val reobserved = mapper.map(available(name = "Changed remotely"), userCleared)

        assertEquals(ContentUiModel.Ready, hydrated.content)
        assertEquals("Persisted name", hydrated.name)
        assertTrue(hydrated.hasHydratedInitialName)
        assertEquals(ContentUiModel.Ready, reobserved.content)
        assertEquals("", reobserved.name)
        assertTrue(reobserved.hasHydratedInitialName)
    }

    @Test
    fun failedProjectionMapsToReadErrorWithoutDiscardingInProgressEditorState() {
        val currentState = ViewState(
            experimentId = experimentId,
            content = ContentUiModel.Ready,
            name = "Unsaved value",
            hasHydratedInitialName = true,
        )

        val mapped = mapper.map(
            ExperimentProjectionObservation.Failed(StorageFailure(StorageOperation.OBSERVE_EXPERIMENT)),
            currentState,
        )

        assertEquals(currentState.copy(content = ContentUiModel.ReadError), mapped)
    }

    private fun available(name: String) = ExperimentProjectionObservation.Available(
        ExperimentProjection(
            id = experimentId,
            displayName = name,
            status = ExperimentStatus.DRAFT,
        ),
    )
}
