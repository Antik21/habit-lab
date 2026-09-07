package com.denis.habitlab.shared.domain.interactor

import com.denis.habitlab.shared.domain.model.ActiveOnboardingProtocolCardinality
import com.denis.habitlab.shared.domain.model.EligibilityConfirmation
import com.denis.habitlab.shared.domain.model.LaunchGateDecision
import com.denis.habitlab.shared.domain.model.LaunchGateSnapshot
import com.denis.habitlab.shared.domain.model.OnboardingProgress
import com.denis.habitlab.shared.domain.model.OnboardingState
import com.denis.habitlab.shared.domain.model.OnboardingStep
import com.denis.habitlab.shared.domain.model.SetupMode

/**
 * Pure owner-layer launch policy. It reads no storage and deliberately never repairs state or
 * creates a protocol, so retrying a gate decision cannot duplicate onboarding work.
 */
class ResolveLaunchGate {
    operator fun invoke(snapshot: LaunchGateSnapshot): LaunchGateDecision {
        val state = snapshot.state
        return when (val progress = state.progress) {
            OnboardingProgress.NotStarted -> LaunchGateDecision.Welcome
            is OnboardingProgress.InProgress -> when (progress.step) {
                OnboardingStep.WELCOME -> LaunchGateDecision.Welcome
                else -> LaunchGateDecision.ResumeOnboarding(nearestReachableCheckpoint(state, progress.step))
            }
            OnboardingProgress.Completed -> resolveCompleted(snapshot)
        }
    }

    private fun resolveCompleted(snapshot: LaunchGateSnapshot): LaunchGateDecision {
        val state = snapshot.state
        val draft = state.selections.setupDraft
        val template = state.selections.template
        return when (val active = snapshot.activeProtocol) {
            ActiveOnboardingProtocolCardinality.None -> {
                if (allSetupPrerequisitesPresent(state) && draft != null) {
                    LaunchGateDecision.ResumeOnboarding(OnboardingStep.SETUP, SetupMode.Recovery)
                } else {
                    LaunchGateDecision.ResumeOnboarding(nearestReachableCheckpoint(state, OnboardingStep.SETUP))
                }
            }
            is ActiveOnboardingProtocolCardinality.ExactlyOne -> {
                val exactMatch = allSetupPrerequisitesPresent(state) && draft != null && template != null &&
                    active.protocol.template == template && active.protocol.configuration.sourceSetupDraft == draft
                if (exactMatch) {
                    LaunchGateDecision.Today
                } else {
                    // An active protocol exists, but it cannot prove this attempt completed. Keep creation
                    // blocked until the future reconciliation owner supplies an explicit repair operation.
                    LaunchGateDecision.ResumeOnboarding(OnboardingStep.SETUP, SetupMode.ControlledBlocking)
                }
            }
        }
    }

    private fun nearestReachableCheckpoint(state: OnboardingState, requested: OnboardingStep): OnboardingStep {
        if (!hasEligibility(state)) return OnboardingStep.WELCOME
        if (requested == OnboardingStep.OUTCOME || !hasGoal(state)) return OnboardingStep.OUTCOME
        if (requested == OnboardingStep.CONTEXT || !hasCurrentContexts(state)) return OnboardingStep.CONTEXT
        if (requested == OnboardingStep.PROTOCOLS || !hasTemplate(state)) return OnboardingStep.PROTOCOLS
        if (requested == OnboardingStep.HEALTH_EXPLANATION || !hasHealth(state)) {
            return OnboardingStep.HEALTH_EXPLANATION
        }
        if (requested == OnboardingStep.STATUS_COVERAGE || !hasSetupDraft(state)) {
            return OnboardingStep.STATUS_COVERAGE
        }
        return OnboardingStep.SETUP
    }

    private fun allSetupPrerequisitesPresent(state: OnboardingState): Boolean =
        hasEligibility(state) && hasGoal(state) && hasCurrentContexts(state) && hasTemplate(state) &&
            hasHealth(state) && hasSetupDraft(state)

    private fun hasEligibility(state: OnboardingState): Boolean =
        state.eligibility == EligibilityConfirmation.CONFIRMED_ADULT

    private fun hasGoal(state: OnboardingState): Boolean = state.selections.goal != null

    private fun hasCurrentContexts(state: OnboardingState): Boolean =
        state.selections.contexts?.requiresConfirmation == false

    private fun hasTemplate(state: OnboardingState): Boolean = state.selections.template != null

    private fun hasHealth(state: OnboardingState): Boolean = state.selections.health != null

    private fun hasSetupDraft(state: OnboardingState): Boolean = state.selections.setupDraft != null
}
