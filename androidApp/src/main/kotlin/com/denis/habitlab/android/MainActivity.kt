package com.denis.habitlab.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.denis.habitlab.shared.app.App
import com.denis.habitlab.shared.app.AppNavigationEventBridge

class MainActivity : ComponentActivity() {
    private val navigationEvents = AppNavigationEventBridge()
    private var hasHandledActionViewIntent = false
    private var lastHandledActionViewUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreHandledActionViewState(savedInstanceState)
        dispatchInitialActionViewIntent(intent)
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_HAS_HANDLED_ACTION_VIEW, hasHandledActionViewIntent)
        if (hasHandledActionViewIntent) {
            outState.putString(STATE_LAST_ACTION_VIEW_URL, lastHandledActionViewUrl)
        }
    }

    /**
     * State is retained outside the untrusted inbound Intent. On recreation the existing URL is
     * skipped, but a different launch URL wins over restored navigation; live intents always run.
     */
    private fun dispatchInitialActionViewIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            val rawUrl = intent.dataString
            if (!hasHandledActionViewIntent || rawUrl != lastHandledActionViewUrl) {
                dispatchActionViewUrl(rawUrl)
            }
        }
    }

    private fun dispatchActionViewIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            dispatchActionViewUrl(intent.dataString)
        }
    }

    private fun dispatchActionViewUrl(rawUrl: String?) {
        hasHandledActionViewIntent = true
        lastHandledActionViewUrl = rawUrl
        navigationEvents.accept(rawUrl)
    }

    private fun restoreHandledActionViewState(savedInstanceState: Bundle?) {
        hasHandledActionViewIntent = savedInstanceState?.getBoolean(
            STATE_HAS_HANDLED_ACTION_VIEW,
            false,
        ) ?: false
        lastHandledActionViewUrl = if (hasHandledActionViewIntent) {
            savedInstanceState?.getString(STATE_LAST_ACTION_VIEW_URL)
        } else {
            null
        }
    }

    private companion object {
        const val STATE_HAS_HANDLED_ACTION_VIEW =
            "com.denis.habitlab.android.state.HAS_HANDLED_ACTION_VIEW"
        const val STATE_LAST_ACTION_VIEW_URL =
            "com.denis.habitlab.android.state.LAST_ACTION_VIEW_URL"
    }
}
