package com.denis.habitlab.shared.app

import androidx.compose.ui.window.ComposeUIViewController
import com.denis.habitlab.shared.presentation.AppPresenter
import platform.UIKit.UIViewController

fun MainViewController(
    presenter: AppPresenter,
    navigationEvents: AppNavigationEventBridge,
): UIViewController = ComposeUIViewController {
    App(presenter = presenter, navigationEvents = navigationEvents)
}
