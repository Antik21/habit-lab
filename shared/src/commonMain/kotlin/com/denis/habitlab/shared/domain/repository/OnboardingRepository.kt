package com.denis.habitlab.shared.domain.repository

import com.denis.habitlab.shared.domain.model.ActiveOnboardingProtocol
import com.denis.habitlab.shared.domain.model.ConfirmedContextSelection
import com.denis.habitlab.shared.domain.model.EligibilityConfirmation
import com.denis.habitlab.shared.domain.model.GoalId
import com.denis.habitlab.shared.domain.model.OnboardingHealthState
import com.denis.habitlab.shared.domain.model.OnboardingProtocolId
import com.denis.habitlab.shared.domain.model.OnboardingState
import com.denis.habitlab.shared.domain.model.ProtocolTemplateId
import com.denis.habitlab.shared.domain.model.SetupDraftReference

/** Focused write boundary for persisted onboarding state and initial protocol configuration. */
interface OnboardingRepository {
    suspend fun confirmEligibility(): OnboardingWriteResult

    suspend fun revokeEligibility(): OnboardingWriteResult

    suspend fun confirmGoal(goal: GoalId): OnboardingWriteResult

    suspend fun confirmContexts(contexts: ConfirmedContextSelection): OnboardingWriteResult

    suspend fun selectProtocol(template: ProtocolTemplateId): OnboardingWriteResult

    suspend fun saveHealthState(health: OnboardingHealthState): OnboardingWriteResult

    suspend fun saveSetupDraftReference(reference: SetupDraftReference): OnboardingWriteResult

    suspend fun createInitialActiveProtocol(
        protocolId: OnboardingProtocolId,
        sourceSetupDraft: SetupDraftReference,
    ): ActiveProtocolWriteResult

    suspend fun appendProtocolConfiguration(
        protocolId: OnboardingProtocolId,
        sourceSetupDraft: SetupDraftReference,
    ): ActiveProtocolWriteResult
}

enum class OnboardingStorageOperation {
    CONFIRM_ELIGIBILITY,
    REVOKE_ELIGIBILITY,
    CONFIRM_GOAL,
    CONFIRM_CONTEXTS,
    SELECT_PROTOCOL,
    SAVE_HEALTH_STATE,
    SAVE_SETUP_DRAFT_REFERENCE,
    CREATE_INITIAL_ACTIVE_PROTOCOL,
    APPEND_PROTOCOL_CONFIGURATION,
    OBSERVE_ONBOARDING_STATE,
    OBSERVE_ONBOARDING_CATALOG,
    OBSERVE_ACTIVE_ONBOARDING_PROTOCOL,
}

data class OnboardingStorageFailure(
    val operation: OnboardingStorageOperation,
)

/** A durable state prerequisite was not met; the requested write was not applied. */
enum class OnboardingPrecondition {
    ELIGIBILITY_CONFIRMED,
    GOAL_CONFIRMED,
    CONTEXTS_CONFIRMED_AND_CURRENT,
    TEMPLATE_SELECTED,
    HEALTH_STATE_RECORDED,
    SETUP_CHECKPOINT,
    SETUP_DRAFT_MATCHES,
    MONOTONIC_SETUP_DRAFT_REVISION,
    ACTIVE_PROTOCOL,
    LATEST_CONFIGURATION_MATCHES_DRAFT,
}

sealed interface OnboardingWriteResult {
    data class Saved(val state: OnboardingState) : OnboardingWriteResult

    /** The stored state was already exactly equal to an idempotent request. */
    data class Unchanged(val state: OnboardingState) : OnboardingWriteResult

    data class Rejected(val precondition: OnboardingPrecondition) : OnboardingWriteResult

    data class Failed(val failure: OnboardingStorageFailure) : OnboardingWriteResult
}

sealed interface ActiveProtocolWriteResult {
    data class Created(val protocol: ActiveOnboardingProtocol) : ActiveProtocolWriteResult

    data class Appended(val protocol: ActiveOnboardingProtocol) : ActiveProtocolWriteResult

    /** The requested source draft already produced the latest immutable configuration. */
    data class Unchanged(val protocol: ActiveOnboardingProtocol) : ActiveProtocolWriteResult

    data class Rejected(val precondition: OnboardingPrecondition) : ActiveProtocolWriteResult

    /** A separate active onboarding protocol already exists; no write has been made. */
    data object ActiveProtocolAlreadyExists : ActiveProtocolWriteResult

    data object MissingSelectedTemplate : ActiveProtocolWriteResult

    /** The persisted selection is corrupt and must be recovered before creating a protocol. */
    data object InvalidSelectedTemplate : ActiveProtocolWriteResult

    data object MissingProtocol : ActiveProtocolWriteResult

    data class Failed(val failure: OnboardingStorageFailure) : ActiveProtocolWriteResult
}

/** Declared explicitly so callers cannot infer a draft is manual from absent health information. */
data class OnboardingEligibilitySnapshot(
    val confirmation: EligibilityConfirmation,
)
