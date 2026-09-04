package com.denis.habitlab.shared.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.denis.habitlab.shared.presentation.AppPresenter
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme

@Composable
fun App(
    presenter: AppPresenter,
    navigationEvents: AppNavigationEventBridge,
) {
    val appUiModel = remember(presenter) { presenter.present() }

    HabitLabTheme {
        Navigation3AppHost(
            appTitle = appUiModel.title,
            navigationEvents = navigationEvents,
        )
    }
}
