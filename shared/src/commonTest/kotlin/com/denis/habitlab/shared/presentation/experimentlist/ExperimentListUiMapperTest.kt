package com.denis.habitlab.shared.presentation.experimentlist

import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.model.ExperimentName
import com.denis.habitlab.shared.domain.model.ExperimentStatus
import com.denis.habitlab.shared.domain.model.ExperimentSummary
import com.denis.habitlab.shared.domain.observer.ExperimentListObservation
import com.denis.habitlab.shared.domain.repository.StorageFailure
import com.denis.habitlab.shared.domain.repository.StorageOperation
import com.denis.habitlab.shared.presentation.model.ExperimentStatusUiModel
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ExperimentListUiMapperTest {
    private val mapper = ExperimentListUiMapper()

    @Test
    fun availableExperimentsKeepTypedIdsBusinessStatusAndClosedAutomationIds() {
        val generatedDraftId = ExperimentId("draft-list001")
        val dailyMovementId = ExperimentId("daily-movement")
        val sleepRoutineId = ExperimentId("sleep-routine")

        val mapped = mapper.map(
            ExperimentListObservation.Available(
                listOf(
                    ExperimentSummary(generatedDraftId, name("Draft habit"), ExperimentStatus.DRAFT),
                    ExperimentSummary(dailyMovementId, name("Active habit"), ExperimentStatus.ACTIVE),
                    ExperimentSummary(sleepRoutineId, name("Sleep habit"), ExperimentStatus.DRAFT),
                ),
            ),
        )

        val content = assertIs<ContentUiModel.Available>(mapped.content)
        assertEquals(
            listOf(
                ExperimentRowUiModel(
                    id = generatedDraftId,
                    name = "Draft habit",
                    status = ExperimentStatusUiModel.DRAFT,
                    automationId = AutomationId.ExperimentListRow,
                ),
                ExperimentRowUiModel(
                    id = dailyMovementId,
                    name = "Active habit",
                    status = ExperimentStatusUiModel.ACTIVE,
                    automationId = AutomationId.ExperimentListDailyMovementRow,
                ),
                ExperimentRowUiModel(
                    id = sleepRoutineId,
                    name = "Sleep habit",
                    status = ExperimentStatusUiModel.DRAFT,
                    automationId = AutomationId.ExperimentListSleepRoutineRow,
                ),
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
