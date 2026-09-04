package com.denis.habitlab.shared.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** Keeps entry-owned UI actions behind one lifecycle gate without gating emitted effects. */
@Composable
internal fun <Action> rememberNavigationActionDispatcher(
    isNavigationActionAllowed: () -> Boolean,
    requiresResumedEntry: (Action) -> Boolean = { true },
    dispatchAction: (Action) -> Unit,
): (Action) -> Unit {
    val currentIsAllowed by rememberUpdatedState(isNavigationActionAllowed)
    val currentRequiresResumedEntry by rememberUpdatedState(requiresResumedEntry)
    val currentDispatchAction by rememberUpdatedState(dispatchAction)

    return remember {
        { action ->
            if (!currentRequiresResumedEntry(action) || currentIsAllowed()) {
                currentDispatchAction(action)
            }
        }
    }
}
