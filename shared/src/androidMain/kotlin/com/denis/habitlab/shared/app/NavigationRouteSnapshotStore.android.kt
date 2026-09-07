package com.denis.habitlab.shared.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
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

    /** Ordinary navigation keeps its visible mutation even if this opportunistic write fails. */
    override suspend fun write(encodedSnapshot: String) {
        commitBestEffortOnSnapshotExecutor {
            preferences.edit().putString(SNAPSHOT_KEY, encodedSnapshot).commit()
        }
    }

    /** Ordinary navigation keeps its visible mutation even if this opportunistic clear fails. */
    override suspend fun clear() {
        commitBestEffortOnSnapshotExecutor {
            preferences.edit().remove(SNAPSHOT_KEY).commit()
        }
    }

    /**
     * SharedPreferences.apply() can leave a process-killed app with the preceding valid stack. A
     * single background executor serializes durable admission writes and this call resumes only
     * after commit (and best-effort invalidation on failure) completes; no disk operation runs on
     * UI. A failed commit is reported so LaunchGate never admits a route as durable.
     */
    override suspend fun writeDurably(encodedSnapshot: String) {
        commitOnSnapshotExecutor {
            preferences.edit().putString(SNAPSHOT_KEY, encodedSnapshot).commit()
        }
    }

    override suspend fun clearDurably() {
        commitOnSnapshotExecutor {
            preferences.edit().remove(SNAPSHOT_KEY).commit()
        }
    }

    private suspend fun commitBestEffortOnSnapshotExecutor(commit: () -> Boolean) {
        try {
            commitOnSnapshotExecutor(commit)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
        }
    }

    private suspend fun commitOnSnapshotExecutor(commit: () -> Boolean) {
        suspendCancellableCoroutine { continuation ->
            val submitted = runCatching {
                snapshotWriteExecutor.execute {
                    val failure = runCatching {
                        check(commit()) { "Navigation snapshot commit returned false" }
                    }.exceptionOrNull()
                    if (failure != null) {
                        // A failed replacement can leave an older but syntactically valid route.
                        // Remove it on the same serial executor before reporting the failure.
                        runCatching { preferences.edit().remove(SNAPSHOT_KEY).commit() }
                    }
                    if (continuation.isActive) {
                        if (failure == null) continuation.resume(Unit) else continuation.resumeWith(Result.failure(failure))
                    }
                }
            }
            if (submitted.isFailure && continuation.isActive) {
                // A rejected executor cannot safely persist, so admission must remain retryable.
                continuation.resumeWith(Result.failure(requireNotNull(submitted.exceptionOrNull())))
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
