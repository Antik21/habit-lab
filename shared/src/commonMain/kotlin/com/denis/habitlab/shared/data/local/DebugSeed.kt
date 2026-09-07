package com.denis.habitlab.shared.data.local

import kotlin.time.Instant

/** Fixed persisted fixture used only by the explicitly enabled debug runtime control. */
internal data class DebugSeed(
    val experiments: List<ExperimentEntity>,
    val checkIns: List<CheckInEntity>,
    val onboardingState: OnboardingStateEntity,
    val activeOnboardingProtocol: OnboardingProtocolEntity,
    val activeOnboardingConfiguration: OnboardingProtocolConfigurationEntity,
) {
    companion object {
        val fixed: DebugSeed by lazy {
            DebugSeed(
                experiments = listOf(
                    ExperimentEntity(
                        id = "daily-movement",
                        displayName = "Daily movement",
                        status = "ACTIVE",
                        activeSlot = ACTIVE_SLOT,
                        createdUtcMillis = millis("2026-01-01T08:00:00Z"),
                        createdOffsetSeconds = 0,
                        createdLocalDate = "2026-01-01",
                        updatedUtcMillis = millis("2026-01-01T08:00:00Z"),
                        updatedOffsetSeconds = 0,
                        updatedLocalDate = "2026-01-01",
                    ),
                    ExperimentEntity(
                        id = "sleep-routine",
                        displayName = "Sleep routine",
                        status = "DRAFT",
                        activeSlot = null,
                        createdUtcMillis = millis("2026-01-01T08:05:00Z"),
                        createdOffsetSeconds = 0,
                        createdLocalDate = "2026-01-01",
                        updatedUtcMillis = millis("2026-01-01T08:05:00Z"),
                        updatedOffsetSeconds = 0,
                        updatedLocalDate = "2026-01-01",
                    ),
                ),
                checkIns = listOf(
                    CheckInEntity(
                        experimentId = "daily-movement",
                        checkInLocalDate = "2026-01-02",
                        outcome = "PERFORMED",
                        occurredUtcMillis = millis("2026-01-02T18:00:00Z"),
                        occurredOffsetSeconds = 0,
                        recordedUtcMillis = millis("2026-01-02T18:01:00Z"),
                        recordedOffsetSeconds = 0,
                        recordedLocalDate = "2026-01-02",
                    ),
                    CheckInEntity(
                        experimentId = "sleep-routine",
                        checkInLocalDate = "2026-01-02",
                        outcome = "SKIPPED",
                        occurredUtcMillis = null,
                        occurredOffsetSeconds = null,
                        recordedUtcMillis = millis("2026-01-02T21:01:00Z"),
                        recordedOffsetSeconds = 0,
                        recordedLocalDate = "2026-01-02",
                    ),
                ),
                onboardingState = OnboardingStateEntity(
                    singletonId = ONBOARDING_SINGLETON_ID,
                    eligibility = ELIGIBILITY_CONFIRMED,
                    progressKind = PROGRESS_COMPLETED,
                    progressStep = null,
                    goalId = "daily-movement",
                    contextsConfirmed = true,
                    contextsRequireConfirmation = false,
                    contextIds = "low-evening-movement",
                    templateId = "after-dinner-walk",
                    hasHealthState = true,
                    healthCapabilityId = "health-record-read",
                    healthCapabilityValue = "AVAILABLE",
                    healthProviderAvailability = "AVAILABLE",
                    healthAccessOutcome = "FULL_ACCESS",
                    healthVisibleRecords = "RECORDS_VISIBLE",
                    healthCoverage = "NOT_ASSESSED",
                    healthFreshness = "NOT_ASSESSED",
                    healthSuitability = "UNDETERMINED",
                    manualPlanState = "EXPLICITLY_SELECTED",
                    setupDraftAttemptId = "debug-onboarding-attempt",
                    setupDraftRevision = 1,
                ),
                activeOnboardingProtocol = OnboardingProtocolEntity(
                    id = "debug-onboarding-protocol",
                    templateId = "after-dinner-walk",
                    status = ONBOARDING_ACTIVE_STATUS,
                    activeSlot = ONBOARDING_ACTIVE_SLOT,
                ),
                activeOnboardingConfiguration = OnboardingProtocolConfigurationEntity(
                    protocolId = "debug-onboarding-protocol",
                    version = 1,
                    sourceSetupDraftId = "debug-onboarding-attempt",
                    sourceSetupDraftRevision = 1,
                ),
            )
        }

        private fun millis(instant: String): Long = Instant.parse(instant).toEpochMilliseconds()

        private const val ACTIVE_SLOT = 1
    }
}
