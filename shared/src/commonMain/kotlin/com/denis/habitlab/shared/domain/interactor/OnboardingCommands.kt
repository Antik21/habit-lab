package com.denis.habitlab.shared.domain.interactor

import com.denis.habitlab.shared.domain.model.ActiveOnboardingProtocol
import com.denis.habitlab.shared.domain.model.ConfirmedContextSelection
import com.denis.habitlab.shared.domain.model.GoalId
import com.denis.habitlab.shared.domain.model.OnboardingHealthState
import com.denis.habitlab.shared.domain.model.OnboardingProtocolId
import com.denis.habitlab.shared.domain.model.ProtocolTemplateId
import com.denis.habitlab.shared.domain.model.SetupDraftReference
import com.denis.habitlab.shared.domain.repository.ActiveProtocolWriteResult
import com.denis.habitlab.shared.domain.repository.OnboardingRepository
import com.denis.habitlab.shared.domain.repository.OnboardingWriteResult

class ConfirmOnboardingEligibility(private val repository: OnboardingRepository) {
    suspend operator fun invoke(): OnboardingWriteResult = repository.confirmEligibility()
}

class RevokeOnboardingEligibility(private val repository: OnboardingRepository) {
    suspend operator fun invoke(): OnboardingWriteResult = repository.revokeEligibility()
}

class ConfirmOnboardingGoal(private val repository: OnboardingRepository) {
    suspend operator fun invoke(goal: GoalId): OnboardingWriteResult = repository.confirmGoal(goal)
}

class ConfirmOnboardingContexts(private val repository: OnboardingRepository) {
    suspend operator fun invoke(contexts: ConfirmedContextSelection): OnboardingWriteResult =
        repository.confirmContexts(contexts)
}

class SelectOnboardingProtocol(private val repository: OnboardingRepository) {
    suspend operator fun invoke(template: ProtocolTemplateId): OnboardingWriteResult =
        repository.selectProtocol(template)
}

class SaveOnboardingHealthState(private val repository: OnboardingRepository) {
    suspend operator fun invoke(health: OnboardingHealthState): OnboardingWriteResult =
        repository.saveHealthState(health)
}

class SaveOnboardingSetupDraftReference(private val repository: OnboardingRepository) {
    suspend operator fun invoke(reference: SetupDraftReference): OnboardingWriteResult =
        repository.saveSetupDraftReference(reference)
}

interface OnboardingProtocolIdSource {
    fun nextId(): OnboardingProtocolId
}

/**
 * DEN-32 owns only the structural primitive. Setup validation, active-experiment reconciliation,
 * and completion routing are deliberately left to their later owners.
 */
class CreateInitialActiveOnboardingProtocol(
    private val repository: OnboardingRepository,
    private val idSource: OnboardingProtocolIdSource,
) {
    suspend operator fun invoke(sourceSetupDraft: SetupDraftReference): ActiveProtocolWriteResult =
        repository.createInitialActiveProtocol(idSource.nextId(), sourceSetupDraft)
}

class AppendOnboardingProtocolConfiguration(private val repository: OnboardingRepository) {
    suspend operator fun invoke(
        protocolId: OnboardingProtocolId,
        sourceSetupDraft: SetupDraftReference,
    ): ActiveProtocolWriteResult = repository.appendProtocolConfiguration(protocolId, sourceSetupDraft)
}
