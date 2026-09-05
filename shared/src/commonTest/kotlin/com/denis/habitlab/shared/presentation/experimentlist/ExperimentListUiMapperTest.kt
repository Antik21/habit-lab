package com.denis.habitlab.shared.presentation.experimentlist

import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.model.ExperimentName
import com.denis.habitlab.shared.domain.model.ExperimentStatus
import com.denis.habitlab.shared.domain.model.ExperimentSummary
import com.denis.habitlab.shared.domain.observer.ExperimentListObservation
import com.denis.habitlab.shared.domain.repository.StorageFailure
import com.denis.habitlab.shared.domain.repository.StorageOperation
import com.denis.habitlab.shared.presentation.model.ExperimentStatusUiModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ExperimentListUiMapperTest {
    private val mapper = ExperimentListUiMapper()

    @Test
    fun availableExperimentsKeepTheirTypedIdsAndBusinessStatusInListRows() {
        val draftId = ExperimentId("draft-list001")
        val activeId = ExperimentId("daily-movement")

        val mapped = mapper.map(
            ExperimentListObservation.Available(
                listOf(
                    ExperimentSummary(draftId, name("Draft habit"), ExperimentStatus.DRAFT),
                    ExperimentSummary(activeId, name("Active habit"), ExperimentStatus.ACTIVE),
                ),
            ),
        )

        val content = assertIs<ContentUiModel.Available>(mapped.content)
        assertEquals(
            listOf(
                ExperimentRowUiModel(draftId, "Draft habit", ExperimentStatusUiModel.DRAFT),
                ExperimentRowUiModel(activeId, "Active habit", ExperimentStatusUiModel.ACTIVE),
            ),
            content.experiments,
        )
    }

    @Test
    fun emptyAndFailedObservationsRemainSemanticallyDistinct() {
        assertEquals(ContentUiModel.Empty, mapper.map(ExperimentListObservation.Available(emptyList())).content)
        assertEquals(
            ContentUiModel.Error,
            mapper.map(
                ExperimentListObservation.Failed(StorageFailure(StorageOperation.OBSERVE_EXPERIMENTS)),
            ).content,
        )
    }

    private fun name(value: String): ExperimentName = requireNotNull(ExperimentName.fromInput(value))
}
