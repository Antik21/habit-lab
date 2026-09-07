package com.denis.habitlab.shared.domain.interactor

import com.denis.habitlab.shared.domain.model.ActiveOnboardingProtocol
import com.denis.habitlab.shared.domain.model.ActiveOnboardingProtocolCardinality
import com.denis.habitlab.shared.domain.model.ConfirmedContextSelection
import com.denis.habitlab.shared.domain.model.EligibilityConfirmation
import com.denis.habitlab.shared.domain.model.GoalId
import com.denis.habitlab.shared.domain.model.HealthAccessOutcome
import com.denis.habitlab.shared.domain.model.HealthCapabilityValue
import com.denis.habitlab.shared.domain.model.HealthProviderAvailability
import com.denis.habitlab.shared.domain.model.LaunchGateDecision
import com.denis.habitlab.shared.domain.model.LaunchGateSnapshot
import com.denis.habitlab.shared.domain.model.OnboardingAttemptId
import com.denis.habitlab.shared.domain.model.OnboardingHealthState
import com.denis.habitlab.shared.domain.model.OnboardingProgress
import com.denis.habitlab.shared.domain.model.OnboardingProtocolId
import com.denis.habitlab.shared.domain.model.OnboardingSelections
import com.denis.habitlab.shared.domain.model.OnboardingState
import com.denis.habitlab.shared.domain.model.OnboardingStep
import com.denis.habitlab.shared.domain.model.ProtocolTemplateId
import com.denis.habitlab.shared.domain.model.SetupDraftReference
import com.denis.habitlab.shared.domain.model.SetupMode
import com.denis.habitlab.shared.domain.model.StoredContextSelection
import com.denis.habitlab.shared.domain.model.VersionedProtocolConfiguration
import com.denis.habitlab.shared.domain.model.VisibleHealthRecordOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveLaunchGateTest {
    private val resolver = ResolveLaunchGate()

    @Test
    fun freshAndEveryReachableInProgressCheckpointResolveToTheCanonicalCheckpoint() {
        assertEquals(LaunchGateDecision.Welcome, resolver(snapshot(state())) )

        listOf(
            OnboardingStep.WELCOME to OnboardingSelections(),
            OnboardingStep.OUTCOME to OnboardingSelections(),
            OnboardingStep.CONTEXT to OnboardingSelections(goal = GoalId.SLEEP_BETTER),
            OnboardingStep.PROTOCOLS to selections(contexts = currentContexts()),
            OnboardingStep.HEALTH_EXPLANATION to selections(template = ProtocolTemplateId.AFTER_DINNER_WALK),
            OnboardingStep.STATUS_COVERAGE to selections(health = health()),
            OnboardingStep.SETUP to selections(setupDraft = draft()),
        ).forEach { (step, selections) ->
            val expected = if (step == OnboardingStep.WELCOME) {
                LaunchGateDecision.Welcome
            } else {
                LaunchGateDecision.ResumeOnboarding(step)
            }
            assertEquals(expected, resolver(snapshot(state(OnboardingProgress.InProgress(step), selections))), step.name)
        }
    }

    @Test
    fun staleInProgressReachabilityFallsBackToTheNearestUpstreamCheckpoint() {
        val complete = selections()
        listOf(
            complete.copy(goal = null) to OnboardingStep.OUTCOME,
            complete.copy(contexts = null) to OnboardingStep.CONTEXT,
            complete.copy(contexts = currentContexts(requiresConfirmation = true)) to OnboardingStep.CONTEXT,
            complete.copy(template = null) to OnboardingStep.PROTOCOLS,
            complete.copy(health = null) to OnboardingStep.HEALTH_EXPLANATION,
            complete.copy(setupDraft = null) to OnboardingStep.STATUS_COVERAGE,
        ).forEach { (selections, expectedCheckpoint) ->
            assertEquals(
                LaunchGateDecision.ResumeOnboarding(expectedCheckpoint),
                resolver(snapshot(state(OnboardingProgress.InProgress(OnboardingStep.SETUP), selections))),
                expectedCheckpoint.name,
            )
        }
        assertEquals(
            LaunchGateDecision.ResumeOnboarding(OnboardingStep.WELCOME),
            resolver(
                snapshot(
                    state(
                        progress = OnboardingProgress.InProgress(OnboardingStep.SETUP),
                        selections = OnboardingSelections(),
                        eligibility = EligibilityConfirmation.UNCONFIRMED,
                    ),
                ),
            ),
        )
    }

    @Test
    fun completedExactTemplateAndDraftMatchResolvesToday() {
        val setupDraft = draft()
        val completed = state(OnboardingProgress.Completed, selections(setupDraft = setupDraft))

        assertEquals(
            LaunchGateDecision.Today,
            resolver(snapshot(completed, active(template = ProtocolTemplateId.AFTER_DINNER_WALK, source = setupDraft))),
        )
    }

    @Test
    fun completedWithoutAnActiveProtocolUsesRecoveryOnlyForAValidSetupDraft() {
        val validDraft = draft()
        assertEquals(
            LaunchGateDecision.ResumeOnboarding(OnboardingStep.SETUP, SetupMode.Recovery),
            resolver(snapshot(state(OnboardingProgress.Completed, selections(setupDraft = validDraft)))),
        )

        listOf(
            selections(setupDraft = null) to OnboardingStep.STATUS_COVERAGE,
            selections(health = null, setupDraft = null) to OnboardingStep.HEALTH_EXPLANATION,
            selections(template = null, health = null, setupDraft = null) to OnboardingStep.PROTOCOLS,
        ).forEach { (selections, expectedCheckpoint) ->
            assertEquals(
                LaunchGateDecision.ResumeOnboarding(expectedCheckpoint),
                resolver(snapshot(state(OnboardingProgress.Completed, selections))),
                expectedCheckpoint.name,
            )
        }
    }

    @Test
    fun completedNonmatchingOrPartiallyInvalidActiveProtocolStaysControlledBlocking() {
        val setupDraft = draft()
        val completed = state(OnboardingProgress.Completed, selections(setupDraft = setupDraft))
        val nonmatchingTemplate = active(ProtocolTemplateId.CALM_EVENING_RITUAL, setupDraft)
        val nonmatchingDraft = active(ProtocolTemplateId.AFTER_DINNER_WALK, draft("attempt-other", 1))

        listOf(nonmatchingTemplate, nonmatchingDraft).forEach { active ->
            assertEquals(
                LaunchGateDecision.ResumeOnboarding(OnboardingStep.SETUP, SetupMode.ControlledBlocking),
                resolver(snapshot(completed, active)),
            )
        }
    }

    @Test
    fun repeatedResolutionIsReferentiallySideEffectFreeAndCannotCreateADuplicate() {
        val input = snapshot(
            state(OnboardingProgress.Completed, selections(setupDraft = draft())),
            active(ProtocolTemplateId.AFTER_DINNER_WALK, draft()),
        )

        repeat(3) {
            assertEquals(LaunchGateDecision.Today, resolver(input))
            assertEquals(OnboardingProgress.Completed, input.state.progress)
            assertEquals(1L, input.activeProtocol.singleConfigurationVersion())
        }
    }

    private fun snapshot(
        state: OnboardingState,
        active: ActiveOnboardingProtocolCardinality = ActiveOnboardingProtocolCardinality.None,
    ) = LaunchGateSnapshot(state, active)

    private fun state(
        progress: OnboardingProgress = OnboardingProgress.NotStarted,
        selections: OnboardingSelections = OnboardingSelections(),
        eligibility: EligibilityConfirmation = if (progress == OnboardingProgress.NotStarted) {
            EligibilityConfirmation.UNCONFIRMED
        } else {
            EligibilityConfirmation.CONFIRMED_ADULT
        },
    ) = OnboardingState(
        eligibility = eligibility,
        progress = progress,
        selections = selections,
    )

    private fun selections(
        contexts: StoredContextSelection = currentContexts(),
        template: ProtocolTemplateId? = ProtocolTemplateId.AFTER_DINNER_WALK,
        health: OnboardingHealthState? = health(),
        setupDraft: SetupDraftReference? = draft(),
    ) = OnboardingSelections(
        goal = GoalId.SLEEP_BETTER,
        contexts = contexts,
        template = template,
        health = health,
        setupDraft = setupDraft,
    )

    private fun currentContexts(requiresConfirmation: Boolean = false) = StoredContextSelection(
        selection = ConfirmedContextSelection.ExplicitlyEmpty,
        requiresConfirmation = requiresConfirmation,
    )

    private fun health() = OnboardingHealthState(
        capabilityValue = HealthCapabilityValue.AVAILABLE,
        providerAvailability = HealthProviderAvailability.AVAILABLE,
        accessOutcome = HealthAccessOutcome.FULL_ACCESS,
        visibleRecords = VisibleHealthRecordOutcome.RECORDS_VISIBLE,
    )

    private fun draft(attempt: String = "attempt-1", revision: Long = 1) = SetupDraftReference(
        attemptId = requireNotNull(OnboardingAttemptId.fromPersisted(attempt)),
        revision = revision,
    )

    private fun active(
        template: ProtocolTemplateId,
        source: SetupDraftReference,
    ): ActiveOnboardingProtocolCardinality = ActiveOnboardingProtocolCardinality.ExactlyOne(
        ActiveOnboardingProtocol(
            id = requireNotNull(OnboardingProtocolId.fromPersisted("onboarding-1")),
            template = template,
            configuration = VersionedProtocolConfiguration(version = 1, sourceSetupDraft = source),
        ),
    )

    private fun ActiveOnboardingProtocolCardinality.singleConfigurationVersion(): Long =
        (this as ActiveOnboardingProtocolCardinality.ExactlyOne).protocol.configuration.version
}
