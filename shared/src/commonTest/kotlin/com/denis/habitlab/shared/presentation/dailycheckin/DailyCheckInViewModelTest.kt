package com.denis.habitlab.shared.presentation.dailycheckin

import com.denis.habitlab.shared.domain.interactor.RecordDailyCheckIn
import com.denis.habitlab.shared.domain.interactor.RecordedAtSource
import com.denis.habitlab.shared.domain.model.DailyCheckIn
import com.denis.habitlab.shared.domain.model.Experiment
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.model.ExperimentName
import com.denis.habitlab.shared.domain.model.ExperimentStatus
import com.denis.habitlab.shared.domain.model.RecordedAt
import com.denis.habitlab.shared.domain.observer.DailyCheckInObservation
import com.denis.habitlab.shared.domain.observer.DailyCheckInObserver
import com.denis.habitlab.shared.domain.observer.ExperimentProjection
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import org.orbitmvi.orbit.test.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class DailyCheckInViewModelTest {
    @Test
    fun recordedCheckInReturnsBackAfterTheSavingState() = runTest {
        val repository = CheckInRepository { checkIn -> RecordDailyCheckInResult.Recorded(checkIn) }
        val viewModel = viewModel(repository)

        viewModel.test(this, initialState = readyState()) {
            viewModel.dispatchAction(Action.SaveClicked)
            expectState { copy(isSaving = true) }
            expectSideEffect(NavigationEffect.Back)
            assertEquals(experimentId, requireNotNull(repository.recorded).experimentId)
        }
    }

    @Test
    fun missingCheckInParentReturnsToRootAfterTheSavingState() = runTest {
        val repository = CheckInRepository { RecordDailyCheckInResult.Missing(experimentId) }
        val viewModel = viewModel(repository)

        viewModel.test(this, initialState = readyState()) {
            viewModel.dispatchAction(Action.SaveClicked)
            expectState { copy(isSaving = true) }
            expectSideEffect(NavigationEffect.PopToRoot)
        }
    }

    @Test
    fun failedCheckInWriteReturnsToAnActionableErrorState() = runTest {
        val repository = CheckInRepository {
            RecordDailyCheckInResult.Failed(StorageFailure(StorageOperation.RECORD_DAILY_CHECK_IN))
        }
        val viewModel = viewModel(repository)

        viewModel.test(this, initialState = readyState()) {
            viewModel.dispatchAction(Action.SaveClicked)
            expectState { copy(isSaving = true) }
            expectState { copy(isSaving = false, commandError = true) }
        }
    }

    @Test
    fun invalidPerformedDateReturnsToAnActionableErrorStateWithoutWriting() = runTest {
        val repository = CheckInRepository { error("Invalid date must not write") }
        val viewModel = viewModel(
            repository = repository,
            recordedAt = RecordedAt(
                utcInstant = Instant.parse("2026-04-02T08:00:00Z"),
                originalOffset = UtcOffset.ZERO,
                localDate = LocalDate.parse("2026-04-02"),
            ),
        )

        viewModel.test(this, initialState = readyState()) {
            viewModel.dispatchAction(Action.SaveClicked)
            expectState { copy(isSaving = true) }
            expectState { copy(isSaving = false, commandError = true) }
            assertNull(repository.recorded)
        }
    }

    @Test
    fun missingObservedParentEmitsPopToRootWithoutTreatingItsCheckInAsAWriteFailure() = runTest {
        val projection = MutableStateFlow<ExperimentProjectionObservation>(availableProjection())
        val checkIn = MutableStateFlow<DailyCheckInObservation>(DailyCheckInObservation.Missing)
        val viewModel = DailyCheckInViewModel(
            experimentId = experimentId,
            localDate = date,
            dailyCheckInObserver = StateFlowCheckInObserver(checkIn),
            experimentProjectionObserver = StateFlowProjectionObserver(projection),
            recordDailyCheckIn = RecordDailyCheckIn(CheckInRepository { error("Unexpected write") }, FixedRecordedAtSource),
            uiMapper = DailyCheckInUiMapper(),
        )

        viewModel.test(this) {
            runOnCreate()
            expectState { copy(content = ContentUiModel.Ready) }

            projection.value = ExperimentProjectionObservation.Missing
            expectSideEffect(NavigationEffect.PopToRoot)
            cancelAndIgnoreRemainingItems()
        }
    }

    private fun viewModel(
        repository: CheckInRepository,
        recordedAt: RecordedAt = FixedRecordedAtSource.now(),
    ): DailyCheckInViewModel = DailyCheckInViewModel(
        experimentId = experimentId,
        localDate = date,
        dailyCheckInObserver = EmptyCheckInObserver,
        experimentProjectionObserver = EmptyProjectionObserver,
        recordDailyCheckIn = RecordDailyCheckIn(repository, object : RecordedAtSource {
            override fun now(): RecordedAt = recordedAt
        }),
        uiMapper = DailyCheckInUiMapper(),
    )

    private fun readyState(): ViewState = ViewState(
        experimentId = experimentId,
        localDateDisplay = date.toString(),
        content = ContentUiModel.Ready,
    )

    private fun availableProjection() = ExperimentProjectionObservation.Available(
        ExperimentProjection(
            id = experimentId,
            displayName = "Daily movement",
            status = ExperimentStatus.DRAFT,
        ),
    )

    private object EmptyCheckInObserver : DailyCheckInObserver {
        override fun observe(experimentId: ExperimentId, localDate: LocalDate): Flow<DailyCheckInObservation> = emptyFlow()
    }

    private object EmptyProjectionObserver : ExperimentProjectionObserver {
        override fun observe(experimentId: ExperimentId): Flow<ExperimentProjectionObservation> = emptyFlow()
    }

    private class StateFlowCheckInObserver(
        private val observations: Flow<DailyCheckInObservation>,
    ) : DailyCheckInObserver {
        override fun observe(experimentId: ExperimentId, localDate: LocalDate): Flow<DailyCheckInObservation> = observations
    }

    private class StateFlowProjectionObserver(
        private val observations: Flow<ExperimentProjectionObservation>,
    ) : ExperimentProjectionObserver {
        override fun observe(experimentId: ExperimentId): Flow<ExperimentProjectionObservation> = observations
    }

    private class CheckInRepository(
        private val result: (DailyCheckIn) -> RecordDailyCheckInResult,
    ) : ExperimentRepository {
        var recorded: DailyCheckIn? = null

        override suspend fun createDraft(draft: Experiment): CreateDraftResult = error("Unexpected create")

        override suspend fun editDraft(
            experimentId: ExperimentId,
            name: ExperimentName,
            updatedAt: RecordedAt,
        ): EditDraftResult = error("Unexpected edit")

        override suspend fun recordDailyCheckIn(checkIn: DailyCheckIn): RecordDailyCheckInResult {
            recorded = checkIn
            return result(checkIn)
        }

        override suspend fun deleteExperiment(experimentId: ExperimentId): DeleteExperimentResult =
            error("Unexpected delete")
    }

    private object FixedRecordedAtSource : RecordedAtSource {
        override fun now(): RecordedAt = RecordedAt(
            utcInstant = Instant.parse("2026-04-01T08:00:00Z"),
            originalOffset = UtcOffset.ZERO,
            localDate = date,
        )
    }

    private companion object {
        val experimentId = ExperimentId("draft-checkin1")
        val date = LocalDate.parse("2026-04-01")
    }
}
