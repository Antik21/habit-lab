package com.denis.habitlab.shared.domain.model

/**
 * Immutable, transactionally observed onboarding input used only to decide a cold launch route.
 *
 * The active protocol shape is explicit: a data source must never manufacture a missing protocol
 * or collapse a corrupt cardinality into a normal empty value.
 */
data class LaunchGateSnapshot(
    val state: OnboardingState,
    val activeProtocol: ActiveOnboardingProtocolCardinality,
)

sealed interface ActiveOnboardingProtocolCardinality {
    data object None : ActiveOnboardingProtocolCardinality

    data class ExactlyOne(val protocol: ActiveOnboardingProtocol) : ActiveOnboardingProtocolCardinality
}

/** A route-neutral outcome. App owns conversion into the common Nav3 stack. */
sealed interface LaunchGateDecision {
    data object Welcome : LaunchGateDecision

    /** The stack always starts at Welcome and ends at [checkpoint]. */
    data class ResumeOnboarding(
        val checkpoint: OnboardingStep,
        val setupMode: SetupMode = SetupMode.Normal,
    ) : LaunchGateDecision

    data object Today : LaunchGateDecision
}

enum class SetupMode {
    Normal,
    Recovery,
    ControlledBlocking,
}
