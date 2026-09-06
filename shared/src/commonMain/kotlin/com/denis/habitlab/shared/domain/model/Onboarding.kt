package com.denis.habitlab.shared.domain.model

import kotlin.jvm.JvmInline

/** Persisted confirmation is intentionally only boolean-like; it never stores date of birth. */
enum class EligibilityConfirmation {
    UNCONFIRMED,
    CONFIRMED_ADULT,
}

sealed interface OnboardingProgress {
    data object NotStarted : OnboardingProgress

    data class InProgress(val step: OnboardingStep) : OnboardingProgress

    data object Completed : OnboardingProgress
}

/** Stable flow checkpoints; these are domain checkpoints, not navigation routes or UI state. */
enum class OnboardingStep {
    WELCOME,
    OUTCOME,
    CONTEXT,
    PROTOCOLS,
    HEALTH_EXPLANATION,
    STATUS_COVERAGE,
    SETUP,
}

/**
 * `null` means unanswered. A confirmed empty selection is [ExplicitlyEmpty], while
 * [ContextId.NOT_SURE_YET] is a real, exclusive answer rather than an empty selection.
 */
sealed interface ConfirmedContextSelection {
    data object ExplicitlyEmpty : ConfirmedContextSelection

    data class Selected(val values: Set<ContextId>) : ConfirmedContextSelection {
        init {
            require(values.isNotEmpty()) { "Use ExplicitlyEmpty for an empty context selection" }
            require(ContextId.NOT_SURE_YET !in values || values.size == 1) {
                "not-sure-yet cannot be selected with a specific context"
            }
        }

        companion object {
            fun from(values: Set<ContextId>): ContextSelectionValidation = when {
                values.isEmpty() -> ContextSelectionValidation.InvalidEmptyUseExplicitlyEmpty
                ContextId.NOT_SURE_YET in values && values.size > 1 ->
                    ContextSelectionValidation.InvalidNotSureYetMixed

                else -> ContextSelectionValidation.Valid(Selected(values.toSet()))
            }
        }
    }
}

sealed interface ContextSelectionValidation {
    data class Valid(val selection: ConfirmedContextSelection.Selected) : ContextSelectionValidation

    data object InvalidEmptyUseExplicitlyEmpty : ContextSelectionValidation

    data object InvalidNotSureYetMixed : ContextSelectionValidation
}

/**
 * A changed goal retains the independently entered answer but explicitly makes it stale for the
 * next ranking attempt. This preserves the answer without treating it as reconfirmed.
 */
data class StoredContextSelection(
    val selection: ConfirmedContextSelection,
    val requiresConfirmation: Boolean,
)

/** Provider-neutral capability vocabulary. It does not name an OS API, provider, or metric. */
enum class HealthCapabilityId(val persistedValue: String) {
    HEALTH_RECORD_READ("health-record-read"),
    ;

    companion object {
        fun parsePersisted(value: String): CatalogIdParseResult<HealthCapabilityId> =
            entries.firstOrNull { it.persistedValue == value }
                ?.let { CatalogIdParseResult.Known<HealthCapabilityId>(it) }
                ?: CatalogIdParseResult.Unknown<HealthCapabilityId>(value)
    }
}

/** A capability value is intentionally separate from access and the result of a data query. */
enum class HealthCapabilityValue {
    UNKNOWN,
    AVAILABLE,
    UNAVAILABLE,
}

enum class HealthProviderAvailability {
    UNKNOWN,
    AVAILABLE,
    UNAVAILABLE,
}

enum class HealthAccessOutcome {
    NOT_REQUESTED,
    UNKNOWN,
    /** The integration reports complete known access; this is not an OS permission claim. */
    FULL_ACCESS,
    /** The integration reports some known access; missing scope remains meaningful. */
    PARTIAL_ACCESS,
    NO_PERMISSION,
    CANCELLED,
    /** A request completed, but the platform deliberately exposes no grant/denial inference. */
    REQUEST_COMPLETED_WITHOUT_ACCESS_INFERENCE,
    REQUEST_ERROR,
}

enum class VisibleHealthRecordOutcome {
    NOT_QUERIED,
    UNKNOWN,
    RECORDS_VISIBLE,
    NO_VISIBLE_RECORDS,
    SOURCE_ERROR,
}

/** No sufficient/insufficient state exists until DEN-37 owns the mapping and thresholds. */
enum class HealthCoverageState {
    NOT_ASSESSED,
    UNKNOWN,
}

enum class HealthFreshnessState {
    NOT_ASSESSED,
    UNKNOWN,
}

enum class HealthSuitabilityState {
    NOT_ASSESSED,
    UNDETERMINED,
}

enum class ManualPlanState {
    NOT_SELECTED,
    EXPLICITLY_SELECTED,
}

/**
 * Axes remain orthogonal. In particular, no visible record, no permission, an unavailable source,
 * and a query error have separate values and never mean numeric zero.
 */
data class OnboardingHealthState(
    val capability: HealthCapabilityId = HealthCapabilityId.HEALTH_RECORD_READ,
    val capabilityValue: HealthCapabilityValue = HealthCapabilityValue.UNKNOWN,
    val providerAvailability: HealthProviderAvailability = HealthProviderAvailability.UNKNOWN,
    val accessOutcome: HealthAccessOutcome = HealthAccessOutcome.NOT_REQUESTED,
    val visibleRecords: VisibleHealthRecordOutcome = VisibleHealthRecordOutcome.NOT_QUERIED,
    val coverage: HealthCoverageState = HealthCoverageState.NOT_ASSESSED,
    val freshness: HealthFreshnessState = HealthFreshnessState.NOT_ASSESSED,
    val suitability: HealthSuitabilityState = HealthSuitabilityState.NOT_ASSESSED,
    val manualPlan: ManualPlanState = ManualPlanState.NOT_SELECTED,
)

/** Correlates a persisted setup draft with immutable protocol configuration history. */
data class SetupDraftReference(
    val attemptId: OnboardingAttemptId,
    val revision: Long,
) {
    init {
        require(revision >= 1) { "Setup draft revision must be positive" }
    }
}

@JvmInline
value class OnboardingAttemptId private constructor(val value: String) {
    companion object {
        private val pattern = Regex("[a-z0-9](?:[a-z0-9-]{0,62})")

        fun fromPersisted(value: String): OnboardingAttemptId? =
            value.takeIf(pattern::matches)?.let(::OnboardingAttemptId)
    }
}

@JvmInline
value class OnboardingProtocolId private constructor(val value: String) {
    companion object {
        private val pattern = Regex("[a-z0-9](?:[a-z0-9-]{0,62})")

        fun fromPersisted(value: String): OnboardingProtocolId? =
            value.takeIf(pattern::matches)?.let(::OnboardingProtocolId)
    }
}

data class OnboardingSelections(
    val goal: GoalId? = null,
    val contexts: StoredContextSelection? = null,
    val template: ProtocolTemplateId? = null,
    val health: OnboardingHealthState? = null,
    val setupDraft: SetupDraftReference? = null,
)

data class OnboardingState(
    val eligibility: EligibilityConfirmation,
    val progress: OnboardingProgress,
    val selections: OnboardingSelections,
)

data class ActiveOnboardingProtocol(
    val id: OnboardingProtocolId,
    val template: ProtocolTemplateId,
    val configuration: VersionedProtocolConfiguration,
)

data class VersionedProtocolConfiguration(
    val version: Long,
    val sourceSetupDraft: SetupDraftReference,
) {
    init {
        require(version >= 1) { "Configuration version must be positive" }
    }
}
