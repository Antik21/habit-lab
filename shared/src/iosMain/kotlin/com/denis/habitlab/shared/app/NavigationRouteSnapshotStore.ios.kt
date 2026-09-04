package com.denis.habitlab.shared.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

@Composable
internal actual fun rememberNavigationRouteSnapshotStore(): NavigationRouteSnapshotStore = remember {
    IosNavigationRouteSnapshotStore
}

private object IosNavigationRouteSnapshotStore : NavigationRouteSnapshotStore {
    override fun read(): String? = NSUserDefaults.standardUserDefaults.stringForKey(SNAPSHOT_KEY)

    override fun write(encodedSnapshot: String) {
        NSUserDefaults.standardUserDefaults.setObject(encodedSnapshot, SNAPSHOT_KEY)
    }

    override fun clear() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(SNAPSHOT_KEY)
    }

    private const val SNAPSHOT_KEY = "com.denis.habitlab.navigation.route_snapshot_v1"
}
