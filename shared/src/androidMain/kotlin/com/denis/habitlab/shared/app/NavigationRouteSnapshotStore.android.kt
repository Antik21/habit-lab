package com.denis.habitlab.shared.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

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

    /**
     * SharedPreferences.apply() can leave a process-killed app with the preceding valid stack. A
     * single background executor serializes commit writes and this suspend call resumes only after
     * commit (and best-effort invalidation on failure) completes; no disk operation runs on UI.
     */
    override suspend fun write(encodedSnapshot: String) {
        commitOnSnapshotExecutor {
            preferences.edit().putString(SNAPSHOT_KEY, encodedSnapshot).commit()
        }
    }

    override suspend fun clear() {
        commitOnSnapshotExecutor {
            preferences.edit().remove(SNAPSHOT_KEY).commit()
        }
    }

    private suspend fun commitOnSnapshotExecutor(commit: () -> Boolean) {
        suspendCancellableCoroutine { continuation ->
            val submitted = runCatching {
                snapshotWriteExecutor.execute {
                    val committed = runCatching(commit).getOrDefault(false)
                    if (!committed) {
                        // A failed replacement can leave an older but syntactically valid route.
                        // Remove it on the same serial executor before reporting this operation done.
                        runCatching { preferences.edit().remove(SNAPSHOT_KEY).commit() }
                    }
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            }
            if (submitted.isFailure && continuation.isActive) {
                // A rejected executor cannot safely persist; avoid throwing from navigation. The
                // next process only sees a snapshot if platform storage itself could not clear it.
                continuation.resume(Unit)
            }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "habitlab.navigation"
        const val SNAPSHOT_KEY = "route_snapshot_v1"
        val snapshotWriteExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "HabitLabNavigationSnapshot").apply { isDaemon = true }
        }
    }
}
