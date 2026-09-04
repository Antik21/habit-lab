package com.denis.habitlab.shared.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberNavigationRouteSnapshotStore(): NavigationRouteSnapshotStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidNavigationRouteSnapshotStore(context) }
}

private class AndroidNavigationRouteSnapshotStore(
    context: Context,
) : NavigationRouteSnapshotStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString(SNAPSHOT_KEY, null)

    override fun write(encodedSnapshot: String) {
        preferences.edit().putString(SNAPSHOT_KEY, encodedSnapshot).apply()
    }

    override fun clear() {
        preferences.edit().remove(SNAPSHOT_KEY).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "habitlab.navigation"
        const val SNAPSHOT_KEY = "route_snapshot_v1"
    }
}
