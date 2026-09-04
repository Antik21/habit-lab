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
    fun persistedOutcomeIsShownSeparatelyAndRepeatedObservationsKeepTheUsersSelection() {
        val initial = mapper.initialState(experimentId, date)
        val firstObservation = mapper.map(skippedCheckIn(), initial)
        val userSelectedPerformed = firstObservation.copy(selectedOutcome = CheckInSelectionUiModel.PERFORMED)

        val reobserved = mapper.map(skippedCheckIn(), userSelectedPerformed)

        assertEquals(ContentUiModel.Ready, firstObservation.content)
        assertEquals(CheckInSelectionUiModel.SKIPPED, firstObservation.selectedOutcome)
        assertEquals(PersistedCheckInStatusUiModel.SKIPPED, firstObservation.persistedOutcome)
        assertTrue(firstObservation.hasHydratedInitialSelection)
        assertEquals(CheckInSelectionUiModel.PERFORMED, reobserved.selectedOutcome)
        assertEquals(PersistedCheckInStatusUiModel.SKIPPED, reobserved.persistedOutcome)
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
        assertTrue(mapped.hasHydratedInitialSelection)
    }

    @Test
    fun failedCheckInReadMapsToReadErrorAndStopsSavingWithoutClearingChoice() {
        val currentState = mapper.initialState(experimentId, date).copy(
            content = ContentUiModel.Ready,
            selectedOutcome = CheckInSelectionUiModel.SKIPPED,
            hasHydratedInitialSelection = true,
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
