package com.denis.habitlab.shared.domain.observer

import com.denis.habitlab.shared.domain.model.ActiveOnboardingProtocol
import com.denis.habitlab.shared.domain.model.OnboardingCatalog
import com.denis.habitlab.shared.domain.model.OnboardingState
import com.denis.habitlab.shared.domain.repository.OnboardingStorageFailure
import kotlinx.coroutines.flow.Flow

interface OnboardingStateObserver {
    fun observeState(): Flow<OnboardingStateObservation>
}

sealed interface OnboardingStateObservation {
    data class Available(val state: OnboardingState) : OnboardingStateObservation

    /** Corrupt or unknown persisted values are surfaced for recovery, never defaulted. */
    data class Invalid(val invalidity: InvalidOnboardingPersistence) : OnboardingStateObservation

    data class Failed(val failure: OnboardingStorageFailure) : OnboardingStateObservation
}

data class InvalidOnboardingPersistence(
    val field: String,
    val rawValue: String,
)

interface OnboardingCatalogObserver {
    fun observeCatalog(): Flow<OnboardingCatalogObservation>
}

sealed interface OnboardingCatalogObservation {
    data class Available(val catalog: OnboardingCatalog) : OnboardingCatalogObservation

    data class Invalid(val invalidity: InvalidOnboardingPersistence) : OnboardingCatalogObservation

    data class Failed(val failure: OnboardingStorageFailure) : OnboardingCatalogObservation
}

interface ActiveOnboardingProtocolObserver {
    fun observeActiveProtocol(): Flow<ActiveOnboardingProtocolObservation>
}

sealed interface ActiveOnboardingProtocolObservation {
    data class Available(val protocol: ActiveOnboardingProtocol) : ActiveOnboardingProtocolObservation

    data object Missing : ActiveOnboardingProtocolObservation

    data class Invalid(val invalidity: InvalidOnboardingPersistence) : ActiveOnboardingProtocolObservation

    data class Failed(val failure: OnboardingStorageFailure) : ActiveOnboardingProtocolObservation
}
