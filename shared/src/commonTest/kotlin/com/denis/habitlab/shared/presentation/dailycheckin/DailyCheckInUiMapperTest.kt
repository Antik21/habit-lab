package com.denis.habitlab.shared.presentation.dailycheckin

import com.denis.habitlab.shared.domain.model.CheckInOutcome
import com.denis.habitlab.shared.domain.model.DailyCheckIn
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.model.RecordedAt
import com.denis.habitlab.shared.domain.observer.DailyCheckInObservation
import com.denis.habitlab.shared.domain.repository.StorageFailure
import com.denis.habitlab.shared.domain.repository.StorageOperation
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class DailyCheckInUiMapperTest {
    private val experimentId = ExperimentId("daily-movement")
    private val date = LocalDate.parse("2026-03-04")
    private val mapper = DailyCheckInUiMapper()

    @Test
    fun missingThenAvailableHydratesThePersistedOutcomeWhenTheUserHasNotSelectedOne() {
        val initial = mapper.initialState(experimentId, date)
        val afterMissing = mapper.map(DailyCheckInObservation.Missing, initial)

        val available = mapper.map(skippedCheckIn(), afterMissing)

        assertEquals(ContentUiModel.Ready, afterMissing.content)
        assertNull(afterMissing.persistedOutcome)
        assertFalse(afterMissing.hasUserSelectedOutcome)
        assertEquals(ContentUiModel.Ready, available.content)
        assertEquals(CheckInSelectionUiModel.SKIPPED, available.selectedOutcome)
        assertEquals(PersistedCheckInStatusUiModel.SKIPPED, available.persistedOutcome)
        assertFalse(available.hasUserSelectedOutcome)
    }

    @Test
    fun missingThenUserSelectionThenAvailablePreservesTheUsersSelection() {
        val afterMissing = mapper.map(DailyCheckInObservation.Missing, mapper.initialState(experimentId, date))
        val userSelectedPerformed = afterMissing.copy(
            selectedOutcome = CheckInSelectionUiModel.PERFORMED,
            hasUserSelectedOutcome = true,
        )

        val available = mapper.map(skippedCheckIn(), userSelectedPerformed)

        assertEquals(CheckInSelectionUiModel.PERFORMED, available.selectedOutcome)
        assertEquals(PersistedCheckInStatusUiModel.SKIPPED, available.persistedOutcome)
        assertTrue(available.hasUserSelectedOutcome)
    }

    @Test
    fun missingCheckInForAnExistingParentIsReadyWithoutAPersistedStatus() {
        val currentState = mapper.initialState(experimentId, date).copy(
            selectedOutcome = CheckInSelectionUiModel.SKIPPED,
        )

        val mapped = mapper.map(DailyCheckInObservation.Missing, currentState)

        assertEquals(ContentUiModel.Ready, mapped.content)
        assertEquals(CheckInSelectionUiModel.SKIPPED, mapped.selectedOutcome)
        assertNull(mapped.persistedOutcome)
        assertFalse(mapped.hasUserSelectedOutcome)
    }

    @Test
    fun failedCheckInReadMapsToReadErrorAndStopsSavingWithoutClearingChoice() {
        val currentState = mapper.initialState(experimentId, date).copy(
            content = ContentUiModel.Ready,
            selectedOutcome = CheckInSelectionUiModel.SKIPPED,
            hasUserSelectedOutcome = true,
            persistedOutcome = PersistedCheckInStatusUiModel.PERFORMED,
            isSaving = true,
        )

        val mapped = mapper.map(
            DailyCheckInObservation.Failed(StorageFailure(StorageOperation.OBSERVE_DAILY_CHECK_IN)),
            currentState,
        )

        assertEquals(ContentUiModel.ReadError, mapped.content)
        assertFalse(mapped.isSaving)
        assertEquals(CheckInSelectionUiModel.SKIPPED, mapped.selectedOutcome)
        assertEquals(PersistedCheckInStatusUiModel.PERFORMED, mapped.persistedOutcome)
    }

    private fun skippedCheckIn() = DailyCheckInObservation.Available(
        DailyCheckIn(
            experimentId = experimentId,
            localDate = date,
            outcome = CheckInOutcome.Skipped,
            recordedAt = RecordedAt(
                utcInstant = Instant.parse("2026-03-04T10:00:00Z"),
                originalOffset = UtcOffset.ZERO,
                localDate = date,
            ),
        ),
    )
}
