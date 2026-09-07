package com.denis.habitlab.shared.presentation.onboardingcheckpoint

import com.denis.habitlab.shared.domain.interactor.ResolveLaunchGate
import com.denis.habitlab.shared.domain.model.LaunchGateDecision
import com.denis.habitlab.shared.domain.observer.LaunchGateSnapshotObservation
import com.denis.habitlab.shared.domain.observer.LaunchGateSnapshotObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

/**
 * App-owned, post-gate observation for durable SETUP completion.
 *
 * The host starts it after any incomplete cold LaunchGate decision. Keeping this monitor outside
 * the Nav3 entry preserves completion delivery when SETUP is popped before Room publishes its
 * completed snapshot; it never participates in the one-snapshot cold decision.
 */
class OnboardingCompletionMonitor(
    private val snapshotObserver: LaunchGateSnapshotObserver,
    private val resolveLaunchGate: ResolveLaunchGate,
) {
    fun observeCompletion(): Flow<NavigationEffect.OnboardingCompleted> = snapshotObserver.observeLaunchSnapshot()
        .filterIsInstance<LaunchGateSnapshotObservation.Available>()
        .map { resolveLaunchGate(it.snapshot) }
        .filter { it is LaunchGateDecision.Today }
        .map { NavigationEffect.OnboardingCompleted }
        .take(1)
}
