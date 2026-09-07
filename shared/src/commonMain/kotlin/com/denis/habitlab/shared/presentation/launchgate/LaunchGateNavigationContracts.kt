package com.denis.habitlab.shared.presentation.launchgate

import com.denis.habitlab.shared.domain.model.LaunchGateDecision as DomainLaunchGateDecision
import com.denis.habitlab.shared.domain.model.SetupMode
import com.denis.habitlab.shared.presentation.navigation.OnboardingStep

/** Presentation route-neutral decision keeps the app navigation owner independent from domain. */
sealed interface LaunchGateDecision {
    data object Welcome : LaunchGateDecision

    data class ResumeOnboarding(
        val checkpoint: OnboardingStep,
        val setupMode: SetupMode,
    ) : LaunchGateDecision

    data object Today : LaunchGateDecision
}

internal fun DomainLaunchGateDecision.toNavigationDecision(): LaunchGateDecision = when (this) {
    DomainLaunchGateDecision.Welcome -> LaunchGateDecision.Welcome
    is DomainLaunchGateDecision.ResumeOnboarding -> LaunchGateDecision.ResumeOnboarding(checkpoint, setupMode)
    DomainLaunchGateDecision.Today -> LaunchGateDecision.Today
}
