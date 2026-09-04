package com.denis.habitlab.shared.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.denis.habitlab.shared.presentation.AppPresenter
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme

@Composable
fun App(
    presenter: AppPresenter,
    navigationEvents: AppNavigationEventBridge,
) {
    val appUiModel = remember(presenter) { presenter.present() }
    val themePreference by presenter.themePreference.collectAsState()

    HabitLabTheme(preference = themePreference) {
        Navigation3AppHost(
            appTitle = appUiModel.title,
            navigationEvents = navigationEvents,
        )
    }
}
