package com.denis.habitlab.shared.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Reads the entry lifecycle when UI input occurs without dropping later structural effects. */
@Composable
internal fun rememberIsNavigationActionAllowed(): () -> Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current

    return remember(lifecycleOwner) {
        { lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED }
    }
}
