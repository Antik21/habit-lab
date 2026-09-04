package com.denis.habitlab.shared.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Drops navigation input while Nav3 has moved this entry out of the resumed state. NavDisplay
 * provides an entry lifecycle, so this protects taps during transitions without introducing a
 * second navigator or lifecycle dependency into presentation.
 */
@Composable
internal fun rememberDropUnlessResumedNavigationAction(action: () -> Unit): () -> Unit {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentAction by rememberUpdatedState(action)

    return remember(lifecycleOwner) {
        {
            if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED) {
                currentAction()
            }
        }
    }
}

@Composable
internal fun <T> rememberDropUnlessResumedNavigationAction(action: (T) -> Unit): (T) -> Unit {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentAction by rememberUpdatedState(action)

    return remember(lifecycleOwner) {
        { value ->
            if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED) {
                currentAction(value)
            }
        }
    }
}
