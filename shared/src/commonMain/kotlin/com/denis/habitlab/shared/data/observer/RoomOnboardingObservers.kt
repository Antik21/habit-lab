package com.denis.habitlab.shared.data.observer

import com.denis.habitlab.shared.data.local.RoomOnboardingLocalDataSource
import com.denis.habitlab.shared.data.mapper.OnboardingDecode
import com.denis.habitlab.shared.data.mapper.toDomain
import com.denis.habitlab.shared.data.mapper.toDomainOnboardingCatalog
import com.denis.habitlab.shared.data.mapper.toDomainOnboardingState
import com.denis.habitlab.shared.domain.observer.ActiveOnboardingProtocolObservation
import com.denis.habitlab.shared.domain.observer.ActiveOnboardingProtocolObserver
import com.denis.habitlab.shared.domain.observer.InvalidOnboardingPersistence
import com.denis.habitlab.shared.domain.observer.OnboardingCatalogObservation
import com.denis.habitlab.shared.domain.observer.OnboardingCatalogObserver
import com.denis.habitlab.shared.domain.observer.OnboardingStateObservation
import com.denis.habitlab.shared.domain.observer.OnboardingStateObserver
import com.denis.habitlab.shared.domain.repository.OnboardingStorageFailure
import com.denis.habitlab.shared.domain.repository.OnboardingStorageOperation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Focused Room flow adapters keep invalid persisted data observable for later recovery work. */
internal class RoomOnboardingObservers(
    private val localDataSource: RoomOnboardingLocalDataSource,
) : OnboardingStateObserver, OnboardingCatalogObserver, ActiveOnboardingProtocolObserver {
    override fun observeState(): Flow<OnboardingStateObservation> = localDataSource.observeState()
        .map { entity ->
            if (entity == null) {
                OnboardingStateObservation.Invalid(InvalidOnboardingPersistence("onboarding_state", "missing"))
            } else {
                when (val decoded = entity.toDomainOnboardingState()) {
                    is OnboardingDecode.Valid -> OnboardingStateObservation.Available(decoded.value)
                    is OnboardingDecode.Invalid -> OnboardingStateObservation.Invalid(decoded.reason)
                }
            }
        }
        .recover(OnboardingStorageOperation.OBSERVE_ONBOARDING_STATE) { failure ->
            OnboardingStateObservation.Failed(failure)
        }

    override fun observeCatalog(): Flow<OnboardingCatalogObservation> = localDataSource.observeCatalogEntries()
        .map { entries ->
            when (val decoded = entries.toDomainOnboardingCatalog()) {
                is OnboardingDecode.Valid -> OnboardingCatalogObservation.Available(decoded.value)
                is OnboardingDecode.Invalid -> OnboardingCatalogObservation.Invalid(decoded.reason)
            }
        }
        .recover(OnboardingStorageOperation.OBSERVE_ONBOARDING_CATALOG) { failure ->
            OnboardingCatalogObservation.Failed(failure)
        }

    override fun observeActiveProtocol(): Flow<ActiveOnboardingProtocolObservation> = localDataSource.observeActiveProtocol()
        .map { snapshot ->
            if (snapshot == null) {
                ActiveOnboardingProtocolObservation.Missing
            } else {
                val configuration = snapshot.configuration ?: return@map ActiveOnboardingProtocolObservation.Invalid(
                    InvalidOnboardingPersistence("onboarding_protocol.configuration", "missing"),
                )
                when (val decoded = snapshot.protocol.toDomain(configuration)) {
                    is OnboardingDecode.Valid -> ActiveOnboardingProtocolObservation.Available(decoded.value)
                    is OnboardingDecode.Invalid -> ActiveOnboardingProtocolObservation.Invalid(decoded.reason)
                }
            }
        }
        .recover(OnboardingStorageOperation.OBSERVE_ACTIVE_ONBOARDING_PROTOCOL) { failure ->
            ActiveOnboardingProtocolObservation.Failed(failure)
        }
}

private fun <T> Flow<T>.recover(
    operation: OnboardingStorageOperation,
    failure: (OnboardingStorageFailure) -> T,
): Flow<T> = catch { throwable ->
    when (throwable) {
        is CancellationException -> throw throwable
        is Error -> throw throwable
        is Exception -> emit(failure(OnboardingStorageFailure(operation)))
        else -> throw throwable
    }
}
