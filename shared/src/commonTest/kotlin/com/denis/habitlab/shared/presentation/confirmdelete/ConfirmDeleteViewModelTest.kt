package com.denis.habitlab.shared.presentation.confirmdelete

import com.denis.habitlab.shared.domain.interactor.DeleteExperiment
import com.denis.habitlab.shared.domain.model.DailyCheckIn
import com.denis.habitlab.shared.domain.model.Experiment
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.model.ExperimentName
import com.denis.habitlab.shared.domain.model.RecordedAt
import com.denis.habitlab.shared.domain.repository.CreateDraftResult
import com.denis.habitlab.shared.domain.repository.DeleteExperimentResult
import com.denis.habitlab.shared.domain.repository.EditDraftResult
import com.denis.habitlab.shared.domain.repository.ExperimentRepository
import com.denis.habitlab.shared.domain.repository.RecordDailyCheckInResult
import com.denis.habitlab.shared.domain.repository.StorageFailure
import com.denis.habitlab.shared.domain.repository.StorageOperation
import com.denis.habitlab.shared.presentation.navigation.DeleteDialogResult
import kotlinx.coroutines.test.runTest
import org.orbitmvi.orbit.test.test
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfirmDeleteViewModelTest {
    @Test
    fun deletedResultResolvesOnceWhenConfirmIsTappedRepeatedly() = runTest {
        val repository = DeleteRepository(DeleteExperimentResult.Deleted(experimentId))
        val viewModel = ConfirmDeleteViewModel(experimentId, DeleteExperiment(repository))

        viewModel.test(this) {
            viewModel.dispatchAction(Action.ConfirmClicked)
            viewModel.dispatchAction(Action.ConfirmClicked)

            expectState { copy(isDeleting = true) }
            expectSideEffect(NavigationEffect.Resolve(DeleteDialogResult.Confirmed(experimentId)))
            assertEquals(1, repository.deleteCalls)
        }
    }

    @Test
    fun missingResultResolvesAsConfirmedBecauseTheRequestedEndStateAlreadyExists() = runTest {
        val repository = DeleteRepository(DeleteExperimentResult.Missing(experimentId))
        val viewModel = ConfirmDeleteViewModel(experimentId, DeleteExperiment(repository))

        viewModel.test(this) {
            viewModel.dispatchAction(Action.ConfirmClicked)
            expectState { copy(isDeleting = true) }
            expectSideEffect(NavigationEffect.Resolve(DeleteDialogResult.Confirmed(experimentId)))
        }
    }

    @Test
    fun failedDeleteReturnsTheDialogToAnActionableErrorState() = runTest {
        val repository = DeleteRepository(
            DeleteExperimentResult.Failed(StorageFailure(StorageOperation.DELETE_EXPERIMENT)),
        )
        val viewModel = ConfirmDeleteViewModel(experimentId, DeleteExperiment(repository))

        viewModel.test(this) {
            viewModel.dispatchAction(Action.ConfirmClicked)
            expectState { copy(isDeleting = true) }
            expectState { copy(isDeleting = false, commandError = true) }
        }
    }

    private class DeleteRepository(
        private val result: DeleteExperimentResult,
    ) : ExperimentRepository {
        var deleteCalls = 0

        override suspend fun createDraft(draft: Experiment): CreateDraftResult = error("Unexpected create")

        override suspend fun editDraft(
            experimentId: ExperimentId,
            name: ExperimentName,
            updatedAt: RecordedAt,
        ): EditDraftResult = error("Unexpected edit")

        override suspend fun recordDailyCheckIn(checkIn: DailyCheckIn): RecordDailyCheckInResult =
            error("Unexpected check-in")

        override suspend fun deleteExperiment(experimentId: ExperimentId): DeleteExperimentResult {
            deleteCalls += 1
            return result
        }
    }

    private companion object {
        val experimentId = ExperimentId("draft-delete3")
    }
}
