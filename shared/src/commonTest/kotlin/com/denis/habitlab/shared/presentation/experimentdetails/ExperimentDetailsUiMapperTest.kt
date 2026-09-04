package com.denis.habitlab.shared.presentation.experimentdetails

import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.model.ExperimentStatus
import com.denis.habitlab.shared.domain.observer.ExperimentProjection
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObservation
import com.denis.habitlab.shared.domain.repository.StorageFailure
import com.denis.habitlab.shared.domain.repository.StorageOperation
import com.denis.habitlab.shared.presentation.model.ExperimentStatusUiModel
import kotlin.test.Test
import kotlin.test.assertEquals

class ExperimentDetailsUiMapperTest {
    private val experimentId = ExperimentId("draft-details1")
    private val mapper = ExperimentDetailsUiMapper()

    @Test
    fun availableProjectionKeepsTheRequestedTypedIdAndMapsTheBusinessStatus() {
        assertEquals(
            ViewState(
                experimentId = experimentId,
                content = ContentUiModel.Available("Read more", ExperimentStatusUiModel.ACTIVE),
            ),
            mapper.map(
                ExperimentProjectionObservation.Available(
                    ExperimentProjection(experimentId, "Read more", ExperimentStatus.ACTIVE),
                ),
                fallbackExperimentId = experimentId,
            ),
        )
    }

    @Test
    fun missingAndFailedDetailsObservationsRemainDistinctLoadingAndErrorStates() {
        assertEquals(
            ViewState(experimentId, ContentUiModel.Loading),
            mapper.map(ExperimentProjectionObservation.Missing, experimentId),
        )
        assertEquals(
            ViewState(experimentId, ContentUiModel.Error),
            mapper.map(
                ExperimentProjectionObservation.Failed(StorageFailure(StorageOperation.OBSERVE_EXPERIMENT)),
                experimentId,
            ),
        )
    }
}
