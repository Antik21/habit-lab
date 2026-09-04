package com.denis.habitlab.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.denis.habitlab.shared.app.App
import com.denis.habitlab.shared.app.AppNavigationEventBridge

class MainActivity : ComponentActivity() {
    private val navigationEvents = AppNavigationEventBridge()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            dispatchActionViewIntent(intent)
        }
        setContent {
            App(
                presenter = (application as HabitLabApplication).appPresenter,
                navigationEvents = navigationEvents,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchActionViewIntent(intent)
    }

    /**
     * Restored Activity instances keep the Nav3 saved stack and do not replay their launch URL.
     * `singleTask` routes every live external request, including repeats, through `onNewIntent`.
     */
    private fun dispatchActionViewIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            navigationEvents.accept(intent.dataString)
        }
    }
}
