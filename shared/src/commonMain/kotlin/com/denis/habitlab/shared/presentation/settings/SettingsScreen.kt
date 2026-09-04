package com.denis.habitlab.shared.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.presentation.navigation.rememberNavigationActionDispatcher
import com.denis.habitlab.shared.presentation.settings.sections.SettingsRuntimeNoteSection
import com.denis.habitlab.shared.presentation.settings.sections.SettingsThemeSection
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import com.denis.habitlab.shared.presentation.ui.component.HabitLabAppScaffold
import com.denis.habitlab.shared.presentation.ui.component.HabitLabToolbar
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.navigation_back_accessibility_label
import habitlab.shared.generated.resources.navigation_back_action_label
import habitlab.shared.generated.resources.settings_title
import org.jetbrains.compose.resources.stringResource
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    isNavigationActionAllowed: () -> Boolean,
    handleNavigationAction: suspend (NavigationEffect) -> Unit,
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { effect ->
        when (effect) { is NavigationEffect -> handleNavigationAction(effect) }
    }
    Content(
        state,
        rememberNavigationActionDispatcher(isNavigationActionAllowed, dispatchAction = viewModel::dispatchAction),
    )
}

@Composable
private fun Content(state: ViewState, onAction: (Action) -> Unit) {
    HabitLabAppScaffold(
        automationId = AutomationId.SettingsScreenRoot,
        toolbar = {
            HabitLabToolbar(
                title = stringResource(Res.string.settings_title),
                backActionLabel = stringResource(Res.string.navigation_back_action_label),
                backActionContentDescription = stringResource(Res.string.navigation_back_accessibility_label),
                backAutomationId = AutomationId.SettingsBack,
                onBack = { onAction(Action.BackClicked) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(HabitLabSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Medium),
        ) {
            SettingsThemeSection(state.themePreference) { onAction(Action.ThemePreferenceSelected(it)) }
            SettingsRuntimeNoteSection()
        }
    }
}

@Preview
@Composable
private fun Preview() { HabitLabTheme { Content(ViewState(), {}) } }
