package com.denis.habitlab.shared.data.observer

import com.denis.habitlab.shared.data.local.RoomOnboardingLocalDataSource
import com.denis.habitlab.shared.data.local.DatabaseReadiness
import com.denis.habitlab.shared.data.local.DatabaseReadinessState
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
import com.denis.habitlab.shared.domain.observer.LaunchGateSnapshotObservation
import com.denis.habitlab.shared.domain.observer.LaunchGateSnapshotObserver
import com.denis.habitlab.shared.domain.model.ActiveOnboardingProtocolCardinality
import com.denis.habitlab.shared.domain.model.LaunchGateSnapshot
import com.denis.habitlab.shared.domain.model.OnboardingProgress
import com.denis.habitlab.shared.domain.repository.OnboardingStorageFailure
import com.denis.habitlab.shared.domain.repository.OnboardingStorageOperation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Focused Room flow adapters keep invalid persisted data observable for later recovery work. */
internal class RoomOnboardingObservers(
    private val localDataSource: RoomOnboardingLocalDataSource,
    private val databaseReadiness: DatabaseReadiness = DatabaseReadiness(DatabaseReadinessState.Ready),
) : OnboardingStateObserver, OnboardingCatalogObserver, ActiveOnboardingProtocolObserver, LaunchGateSnapshotObserver {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeLaunchSnapshot(): Flow<LaunchGateSnapshotObservation> = databaseReadiness.state
        .filterNot { it is DatabaseReadinessState.Initializing }
        .flatMapLatest { readiness ->
            when (readiness) {
                DatabaseReadinessState.Ready -> localDataSource.observeLaunchGateSnapshotRows()
                    .map(::decodeLaunchGateSnapshot)
                    .recover(OnboardingStorageOperation.OBSERVE_LAUNCH_GATE_SNAPSHOT) { failure ->
                        LaunchGateSnapshotObservation.Failed(failure)
                    }

                is DatabaseReadinessState.Failed -> flowOf(
                    LaunchGateSnapshotObservation.Failed(
                        OnboardingStorageFailure(OnboardingStorageOperation.OBSERVE_LAUNCH_GATE_SNAPSHOT),
                    ),
                )

                DatabaseReadinessState.Initializing -> error("Database readiness must be terminal")
            }
        }
}

private fun decodeLaunchGateSnapshot(rows: List<com.denis.habitlab.shared.data.local.LaunchGateSnapshotRow>):
    LaunchGateSnapshotObservation {
    val row = rows.firstOrNull() ?: return launchInvalid("launch_gate", "missing snapshot row")
    val state = row.state ?: return launchInvalid("onboarding_state", "missing")
    if (rows.any { it.activeProtocolCount != row.activeProtocolCount || it.state != state }) {
        return launchInvalid("launch_gate", "incoherent query projection")
    }
    val decodedState = when (val decoded = state.toDomainOnboardingState()) {
        is OnboardingDecode.Valid -> decoded.value
        is OnboardingDecode.Invalid -> return LaunchGateSnapshotObservation.Invalid(decoded.reason)
    }
    val active = when (row.activeProtocolCount) {
        0L -> {
            if (rows.size != 1 || row.protocol != null || row.configuration != null) {
                return launchInvalid("onboarding_protocol.active_slot", "zero count has protocol data")
            }
            ActiveOnboardingProtocolCardinality.None
        }
        1L -> {
            if (rows.size != 1 || row.protocol == null || row.configuration == null) {
                return launchInvalid("onboarding_protocol.active_slot", "one count has incomplete protocol data")
            }
            when (val decoded = row.protocol.toDomain(row.configuration)) {
                is OnboardingDecode.Valid -> ActiveOnboardingProtocolCardinality.ExactlyOne(decoded.value)
                is OnboardingDecode.Invalid -> return LaunchGateSnapshotObservation.Invalid(decoded.reason)
            }
        }
        else -> return launchInvalid("onboarding_protocol.active_slot", "cardinality=${row.activeProtocolCount}")
    }
    if (active is ActiveOnboardingProtocolCardinality.ExactlyOne && decodedState.progress !is OnboardingProgress.Completed) {
        return launchInvalid("onboarding_state.progress", "active protocol requires completed checkpoint")
    }
    return LaunchGateSnapshotObservation.Available(LaunchGateSnapshot(decodedState, active))
}

private fun launchInvalid(field: String, rawValue: String): LaunchGateSnapshotObservation.Invalid =
    LaunchGateSnapshotObservation.Invalid(InvalidOnboardingPersistence(field, rawValue))

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
