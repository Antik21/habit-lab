package com.denis.habitlab.shared.app

import androidx.compose.ui.window.ComposeUIViewController
import com.denis.habitlab.shared.core.platform.PlatformDescriptor
import com.denis.habitlab.shared.data.local.createHabitLabDatabase
import com.denis.habitlab.shared.di.HabitLabRuntime
import com.denis.habitlab.shared.di.initHabitLabRuntime
import com.denis.habitlab.shared.presentation.AppPresenter
import platform.UIKit.UIViewController

fun MainViewController(
    presenter: AppPresenter,
    navigationEvents: AppNavigationEventBridge,
): UIViewController = ComposeUIViewController {
    App(presenter = presenter, navigationEvents = navigationEvents)
}

/** iOS host bootstrap owns the build flag; platform data code owns only its sandboxed DB path. */
fun createIosHabitLabRuntime(
    platformDescriptor: PlatformDescriptor,
    isDebugBuild: Boolean,
): HabitLabRuntime {
    val runtime = initHabitLabRuntime(
        platformDescriptor = platformDescriptor,
        database = createHabitLabDatabase(),
        isDebugBuild = isDebugBuild,
    )
    runtime.initialize()
    return runtime
}
