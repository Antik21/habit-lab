package com.denis.habitlab.shared.presentation.launchgate

import com.denis.habitlab.shared.domain.interactor.ResolveLaunchGate
import com.denis.habitlab.shared.domain.model.ActiveOnboardingProtocolCardinality
import com.denis.habitlab.shared.domain.model.EligibilityConfirmation
import com.denis.habitlab.shared.domain.model.LaunchGateSnapshot
import com.denis.habitlab.shared.domain.model.OnboardingProgress
import com.denis.habitlab.shared.domain.model.OnboardingSelections
import com.denis.habitlab.shared.domain.model.OnboardingState
import com.denis.habitlab.shared.domain.observer.InvalidOnboardingPersistence
import com.denis.habitlab.shared.domain.observer.LaunchGateSnapshotObservation
import com.denis.habitlab.shared.domain.observer.LaunchGateSnapshotObserver
import com.denis.habitlab.shared.domain.repository.OnboardingStorageFailure
import com.denis.habitlab.shared.domain.repository.OnboardingStorageOperation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.orbitmvi.orbit.test.test
import kotlin.test.Test
import kotlin.test.assertEquals

class LaunchGateViewModelTest {
    @Test
    fun invalidSnapshotClearsOnlyTheObsoleteRouteAndEndsInRetryableInvalidState() = runTest {
        val observer = RecordingObserver(
            listOf(LaunchGateSnapshotObservation.Invalid(InvalidOnboardingPersistence("onboarding_state", "missing"))),
        )
        val viewModel = LaunchGateViewModel(observer, ResolveLaunchGate())

        viewModel.test(this) {
            runOnCreate()

            expectSideEffect(NavigationEffect.ClearObsoleteSnapshot)
            expectState { copy(content = ContentUiModel.Invalid) }
            assertEquals(1, observer.collectionCount)
        }
    }

    @Test
    fun failedSnapshotClearsOnlyTheObsoleteRouteAndEndsInRetryableFailedState() = runTest {
        val observer = RecordingObserver(
            listOf(
                LaunchGateSnapshotObservation.Failed(
                    OnboardingStorageFailure(OnboardingStorageOperation.OBSERVE_LAUNCH_GATE_SNAPSHOT),
                ),
            ),
        )
        val viewModel = LaunchGateViewModel(observer, ResolveLaunchGate())

        viewModel.test(this) {
            runOnCreate()

            expectSideEffect(NavigationEffect.ClearObsoleteSnapshot)
            expectState { copy(content = ContentUiModel.Failed) }
            assertEquals(1, observer.collectionCount)
        }
    }

    @Test
    fun retryUsesOneFreshSnapshotAfterInvalidAndResolvesWithoutAnotherCleanup() = runTest {
        val observer = RecordingObserver(
            listOf(LaunchGateSnapshotObservation.Invalid(InvalidOnboardingPersistence("state", "invalid"))),
            listOf(availableSnapshot()),
        )
        val viewModel = LaunchGateViewModel(observer, ResolveLaunchGate())

        viewModel.test(this) {
            runOnCreate()
            expectSideEffect(NavigationEffect.ClearObsoleteSnapshot)
            expectState { copy(content = ContentUiModel.Invalid) }

            viewModel.dispatchAction(Action.RetryClicked)
            expectState { copy(content = ContentUiModel.Loading) }
            expectSideEffect(NavigationEffect.Resolve(LaunchGateDecision.Welcome))
            assertEquals(2, observer.collectionCount)
            expectNoItems()
        }
    }

    @Test
    fun eachAttemptConsumesExactlyOneAtomicSnapshotEvenWhenTheSourceWouldEmitMore() = runTest {
        val observer = RecordingObserver(
            listOf(
                availableSnapshot(),
                LaunchGateSnapshotObservation.Invalid(InvalidOnboardingPersistence("unexpected", "second emission")),
            ),
        )
        val viewModel = LaunchGateViewModel(observer, ResolveLaunchGate())

        viewModel.test(this) {
            runOnCreate()

            expectSideEffect(NavigationEffect.Resolve(LaunchGateDecision.Welcome))
            assertEquals(1, observer.collectionCount)
            assertEquals(1, observer.collectedItems)
            expectNoItems()
        }
    }

    private fun availableSnapshot() = LaunchGateSnapshotObservation.Available(
        LaunchGateSnapshot(
            state = OnboardingState(
                eligibility = EligibilityConfirmation.UNCONFIRMED,
                progress = OnboardingProgress.NotStarted,
                selections = OnboardingSelections(),
            ),
            activeProtocol = ActiveOnboardingProtocolCardinality.None,
        ),
    )

    private class RecordingObserver(
        private vararg val attempts: List<LaunchGateSnapshotObservation>,
    ) : LaunchGateSnapshotObserver {
        var collectionCount = 0
        var collectedItems = 0

        override fun observeLaunchSnapshot(): Flow<LaunchGateSnapshotObservation> = flow {
            val attempt = attempts.getOrNull(collectionCount) ?: error("Unexpected collection")
            collectionCount += 1
            attempt.forEach { observation ->
                collectedItems += 1
                emit(observation)
            }
        }
    }
}
