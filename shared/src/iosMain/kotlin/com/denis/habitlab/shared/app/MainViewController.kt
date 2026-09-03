package com.denis.habitlab.shared.app

import androidx.compose.ui.window.ComposeUIViewController
import com.denis.habitlab.shared.presentation.AppPresenter
import platform.UIKit.UIViewController

fun MainViewController(presenter: AppPresenter): UIViewController = ComposeUIViewController {
    App(presenter = presenter)
}
