package com.denis.habitlab.shared.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class OnboardingCatalogTest {
    @Test
    fun persistedCatalogIdsAreClosedOrderedAndReportUnknownValuesExplicitly() {
        assertEquals(
            listOf(
                "sleep-better",
                "wake-refreshed",
                "morning-energy",
                "calm-evening",
                "daily-movement",
            ),
            GoalId.entries.map(GoalId::persistedValue),
        )
        assertEquals(
            listOf(
                "low-evening-movement",
                "screen-before-sleep",
                "irregular-sleep-time",
                "late-meal",
                "hard-to-unwind",
                "variable-schedule",
                "not-sure-yet",
            ),
            ContextId.entries.map(ContextId::persistedValue),
        )
        assertEquals(
            listOf(
                "after-dinner-walk",
                "calm-evening-ritual",
                "regular-sleep-schedule",
            ),
            ProtocolTemplateId.entries.map(ProtocolTemplateId::persistedValue),
        )
        assertEquals(
            listOf(
                "sleep-duration",
                "sleep-session-duration",
                "morning-energy",
                "subjective-sleep-quality",
                "sleep-timing-variability",
                "sleep-attempt-time",
            ),
            MetricId.entries.map(MetricId::persistedValue),
        )

        assertEquals(
            CatalogIdParseResult.Known(GoalId.SLEEP_BETTER),
            GoalId.parsePersisted("sleep-better"),
        )
        assertEquals(
            CatalogIdParseResult.Known(ContextId.NOT_SURE_YET),
            ContextId.parsePersisted("not-sure-yet"),
        )
        assertEquals(
            CatalogIdParseResult.Known(ProtocolTemplateId.AFTER_DINNER_WALK),
            ProtocolTemplateId.parsePersisted("after-dinner-walk"),
        )
        assertEquals(
            CatalogIdParseResult.Known(MetricId.SLEEP_DURATION),
            MetricId.parsePersisted("sleep-duration"),
        )
        assertEquals(
            CatalogIdParseResult.Unknown("future-catalog-id"),
            GoalId.parsePersisted("future-catalog-id"),
        )
        assertEquals(
            CatalogIdParseResult.Unknown("unknown-capability"),
            HealthCapabilityId.parsePersisted("unknown-capability"),
        )
    }

    @Test
    fun contextsKeepUnansweredConfirmedEmptyAndNotSureYetDistinct() {
        assertEquals(
            ContextSelectionValidation.InvalidEmptyUseExplicitlyEmpty,
            ConfirmedContextSelection.Selected.from(emptySet()),
        )
        assertEquals(
            ContextSelectionValidation.Valid(
                ConfirmedContextSelection.Selected(setOf(ContextId.NOT_SURE_YET)),
            ),
            ConfirmedContextSelection.Selected.from(setOf(ContextId.NOT_SURE_YET)),
        )
        assertEquals(
            ContextSelectionValidation.InvalidNotSureYetMixed,
            ConfirmedContextSelection.Selected.from(
                setOf(ContextId.NOT_SURE_YET, ContextId.LATE_MEAL),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            ConfirmedContextSelection.Selected(
                setOf(ContextId.NOT_SURE_YET, ContextId.LATE_MEAL),
            )
        }
        assertIs<ConfirmedContextSelection.ExplicitlyEmpty>(ConfirmedContextSelection.ExplicitlyEmpty)
    }

    @Test
    fun healthOutcomesRemainSeparateProviderNeutralStatesWithoutNumericFallbacks() {
        assertEquals(
            listOf(
                HealthAccessOutcome.FULL_ACCESS,
                HealthAccessOutcome.PARTIAL_ACCESS,
                HealthAccessOutcome.NO_PERMISSION,
                HealthAccessOutcome.CANCELLED,
                HealthAccessOutcome.REQUEST_COMPLETED_WITHOUT_ACCESS_INFERENCE,
                HealthAccessOutcome.REQUEST_ERROR,
            ),
            HealthAccessOutcome.entries.filter { it !in setOf(HealthAccessOutcome.NOT_REQUESTED, HealthAccessOutcome.UNKNOWN) },
        )
        assertEquals(VisibleHealthRecordOutcome.NOT_QUERIED, OnboardingHealthState().visibleRecords)
        assertEquals(HealthCoverageState.NOT_ASSESSED, OnboardingHealthState().coverage)
        assertEquals(HealthFreshnessState.NOT_ASSESSED, OnboardingHealthState().freshness)
        assertEquals(HealthSuitabilityState.NOT_ASSESSED, OnboardingHealthState().suitability)
    }
}
