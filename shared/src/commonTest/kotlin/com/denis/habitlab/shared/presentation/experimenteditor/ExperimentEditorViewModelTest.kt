package com.denis.habitlab.shared.presentation.experimenteditor

import com.denis.habitlab.shared.domain.interactor.CreateExperimentDraft
import com.denis.habitlab.shared.domain.interactor.EditExperimentDraft
import com.denis.habitlab.shared.domain.interactor.ExperimentIdSource
import com.denis.habitlab.shared.domain.interactor.RecordedAtSource
import com.denis.habitlab.shared.domain.model.DailyCheckIn
import com.denis.habitlab.shared.domain.model.Experiment
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.model.ExperimentName
import com.denis.habitlab.shared.domain.model.RecordedAt
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObservation
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObserver
import com.denis.habitlab.shared.domain.repository.CreateDraftResult
import com.denis.habitlab.shared.domain.repository.DeleteExperimentResult
import com.denis.habitlab.shared.domain.repository.EditDraftResult
import com.denis.habitlab.shared.domain.repository.ExperimentRepository
import com.denis.habitlab.shared.domain.repository.RecordDailyCheckInResult
import com.denis.habitlab.shared.domain.repository.StorageFailure
import com.denis.habitlab.shared.domain.repository.StorageOperation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import org.orbitmvi.orbit.test.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ExperimentEditorViewModelTest {
    @Test
    fun blankNameIsRejectedBeforeTheCreateCommandRuns() = runTest {
        val repository = EditorRepository()
        val viewModel = createViewModel(repository = repository)

        viewModel.test(this) {
            viewModel.dispatchAction(Action.NameChanged("   "))
            expectState { copy(name = "   ") }

            viewModel.dispatchAction(Action.SaveClicked)
            expectState { copy(validationError = true) }
            assertNull(repository.createdDraft)
        }
    }

    @Test
    fun createResultEmitsTheGeneratedExperimentIdAfterSavingState() = runTest {
        val generatedId = ExperimentId("draft-editor1")
        val repository = EditorRepository(create = { draft -> CreateDraftResult.Created(draft) })
        val viewModel = createViewModel(repository = repository, generatedId = generatedId)

        viewModel.test(this) {
            viewModel.dispatchAction(Action.NameChanged("Read more"))
            expectState { copy(name = "Read more") }

            viewModel.dispatchAction(Action.SaveClicked)
            expectState { copy(isSaving = true) }
            expectSideEffect(NavigationEffect.SaveComplete(generatedId))
            assertEquals(generatedId, requireNotNull(repository.createdDraft).id)
        }
    }

    @Test
    fun createStorageFailureReturnsTheEditorToAnActionableErrorStateWithoutSaveComplete() = runTest {
        val repository = EditorRepository(
            create = { CreateDraftResult.Failed(StorageFailure(StorageOperation.CREATE_DRAFT)) },
        )
        val viewModel = createViewModel(repository = repository)

        viewModel.test(this) {
            viewModel.dispatchAction(Action.NameChanged("Read more"))
            expectState { copy(name = "Read more") }

            viewModel.dispatchAction(Action.SaveClicked)
            expectState { copy(isSaving = true) }
            expectState { copy(isSaving = false, commandError = true) }
            expectNoItems()
        }
    }

    @Test
    fun updatedEditResultEmitsSaveCompleteForTheEditedExperiment() = runTest {
        val experimentId = ExperimentId("draft-editor4")
        val repository = EditorRepository(edit = { id, _, _ -> EditDraftResult.Updated(id) })
        val viewModel = editViewModel(experimentId, repository)

        viewModel.test(
            testScope = this,
            initialState = readyEditState(experimentId),
        ) {
            viewModel.dispatchAction(Action.SaveClicked)
            expectState { copy(isSaving = true) }
            expectSideEffect(NavigationEffect.SaveComplete(experimentId))
        }
    }

    @Test
    fun editMissingMapsToPopToRootInsteadOfReportingASaveError() = runTest {
        val experimentId = ExperimentId("draft-editor2")
        val repository = EditorRepository(edit = { id, _, _ -> EditDraftResult.Missing(id) })
        val viewModel = editViewModel(experimentId, repository)

        viewModel.test(
            testScope = this,
            initialState = readyEditState(experimentId),
        ) {
            viewModel.dispatchAction(Action.SaveClicked)
            expectState { copy(isSaving = true) }
            expectSideEffect(NavigationEffect.PopToRoot)
        }
    }

    @Test
    fun editStorageFailureReturnsTheEditorToAnActionableErrorState() = runTest {
        val experimentId = ExperimentId("draft-editor3")
        val repository = EditorRepository(
            edit = { _, _, _ -> EditDraftResult.Failed(StorageFailure(StorageOperation.EDIT_DRAFT)) },
        )
        val viewModel = editViewModel(experimentId, repository)

        viewModel.test(
            testScope = this,
            initialState = readyEditState(experimentId),
        ) {
            viewModel.dispatchAction(Action.SaveClicked)
            expectState { copy(isSaving = true) }
            expectState { copy(isSaving = false, commandError = true) }
        }
    }

    private fun createViewModel(
        repository: EditorRepository,
        generatedId: ExperimentId = ExperimentId("draft-editor0"),
    ): ExperimentEditorViewModel = ExperimentEditorViewModel(
        experimentId = null,
        projectionObserver = EmptyProjectionObserver,
        createExperimentDraft = CreateExperimentDraft(
            repository = repository,
            idSource = object : ExperimentIdSource {
                override fun nextDraftId(): ExperimentId = generatedId
            },
            recordedAtSource = FixedRecordedAtSource,
        ),
        editExperimentDraft = EditExperimentDraft(repository, FixedRecordedAtSource),
        uiMapper = ExperimentEditorUiMapper(),
    )

    private fun editViewModel(
        experimentId: ExperimentId,
        repository: EditorRepository,
    ): ExperimentEditorViewModel = ExperimentEditorViewModel(
        experimentId = experimentId,
        projectionObserver = EmptyProjectionObserver,
        createExperimentDraft = CreateExperimentDraft(
            repository = repository,
            idSource = object : ExperimentIdSource {
                override fun nextDraftId(): ExperimentId = ExperimentId("draft-unused1")
            },
            recordedAtSource = FixedRecordedAtSource,
        ),
        editExperimentDraft = EditExperimentDraft(repository, FixedRecordedAtSource),
        uiMapper = ExperimentEditorUiMapper(),
    )

    private fun readyEditState(experimentId: ExperimentId): ViewState = ViewState(
        experimentId = experimentId,
        content = ContentUiModel.Ready,
        name = "Persisted name",
        hasHydratedInitialName = true,
    )

    private object EmptyProjectionObserver : ExperimentProjectionObserver {
        override fun observe(experimentId: ExperimentId): Flow<ExperimentProjectionObservation> = emptyFlow()
    }

    private object FixedRecordedAtSource : RecordedAtSource {
        override fun now(): RecordedAt = RecordedAt(
            utcInstant = Instant.parse("2026-04-01T08:00:00Z"),
            originalOffset = UtcOffset.ZERO,
            localDate = LocalDate.parse("2026-04-01"),
        )
    }

    private class EditorRepository(
        private val create: (Experiment) -> CreateDraftResult = { error("Unexpected create") },
        private val edit: (ExperimentId, ExperimentName, RecordedAt) -> EditDraftResult = { _, _, _ ->
            error("Unexpected edit")
        },
    ) : ExperimentRepository {
        var createdDraft: Experiment? = null

        override suspend fun createDraft(draft: Experiment): CreateDraftResult {
            createdDraft = draft
            return create(draft)
        }

        override suspend fun editDraft(
            experimentId: ExperimentId,
            name: ExperimentName,
            updatedAt: RecordedAt,
        ): EditDraftResult = edit(experimentId, name, updatedAt)

        override suspend fun recordDailyCheckIn(checkIn: DailyCheckIn): RecordDailyCheckInResult =
            error("Unexpected check-in")

        override suspend fun deleteExperiment(experimentId: ExperimentId): DeleteExperimentResult =
            error("Unexpected delete")
    }
}
