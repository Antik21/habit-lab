package com.denis.habitlab.shared.presentation.onboardingcheckpoint

import com.denis.habitlab.shared.domain.model.OnboardingStep
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId

/** Static route-step mapping until the downstream onboarding screen owners replace each contour. */
class OnboardingCheckpointUiMapper {
    fun map(step: OnboardingStep): CheckpointUiModel = CheckpointUiModel(
        step = step,
        automationId = when (step) {
            OnboardingStep.WELCOME -> AutomationId.OnboardingWelcomeScreenRoot
            OnboardingStep.OUTCOME -> AutomationId.OnboardingOutcomeScreenRoot
            OnboardingStep.CONTEXT -> AutomationId.OnboardingContextScreenRoot
            OnboardingStep.PROTOCOLS -> AutomationId.OnboardingProtocolsScreenRoot
            OnboardingStep.HEALTH_EXPLANATION -> AutomationId.OnboardingHealthExplanationScreenRoot
            OnboardingStep.STATUS_COVERAGE -> AutomationId.OnboardingStatusCoverageScreenRoot
            OnboardingStep.SETUP -> AutomationId.OnboardingSetupScreenRoot
        },
    )
}
