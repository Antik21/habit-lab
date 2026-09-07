package com.denis.habitlab.shared.presentation.launchgate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denis.habitlab.shared.domain.interactor.ResolveLaunchGate
import com.denis.habitlab.shared.domain.observer.LaunchGateSnapshotObservation
import com.denis.habitlab.shared.domain.observer.LaunchGateSnapshotObserver
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container
import org.orbitmvi.orbit.syntax.Syntax

/** Entry-scoped owner of the cold-start read. It only resolves routes and never writes onboarding data. */
class LaunchGateViewModel(
    private val snapshotObserver: LaunchGateSnapshotObserver,
    private val resolveLaunchGate: ResolveLaunchGate,
) : ViewModel(), ContainerHost<ViewState, SideEffect> {
    override val container: Container<ViewState, SideEffect> = viewModelScope.container(
        initialState = ViewState(),
        onCreate = { observeLaunchSnapshot() },
    )

    fun dispatchAction(action: Action) {
        when (action) {
            Action.RetryClicked -> intent { observeLaunchSnapshot() }
        }
    }

    /** A route is admitted only after its platform snapshot commit succeeds; expose a retry path. */
    fun onRoutePersistenceFailed() {
        intent { reduce { ViewState(ContentUiModel.Failed) } }
    }

    private suspend fun Syntax<ViewState, SideEffect>.observeLaunchSnapshot() {
        reduce { ViewState(ContentUiModel.Loading) }
        // A gate attempt consumes exactly one atomic Room snapshot. Invalid/failed attempts end
        // here so Retry can start a fresh durable read instead of adding a second collector.
        snapshotObserver.observeLaunchSnapshot().take(1).collect { observation ->
            when (observation) {
                is LaunchGateSnapshotObservation.Available -> {
                    postSideEffect(NavigationEffect.Resolve(resolveLaunchGate(observation.snapshot).toNavigationDecision()))
                }
                is LaunchGateSnapshotObservation.Invalid -> {
                    postSideEffect(NavigationEffect.ClearObsoleteSnapshot)
                    reduce { ViewState(ContentUiModel.Invalid) }
                }
                is LaunchGateSnapshotObservation.Failed -> {
                    postSideEffect(NavigationEffect.ClearObsoleteSnapshot)
                    reduce { ViewState(ContentUiModel.Failed) }
                }
            }
        }
    }
}
