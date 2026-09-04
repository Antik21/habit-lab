package com.denis.habitlab.shared.presentation.settings

import androidx.compose.runtime.Immutable
import com.denis.habitlab.shared.domain.model.ThemePreference

@Immutable
data class ViewState(val themePreference: ThemePreference = ThemePreference.SYSTEM)

sealed interface Action {
    data object BackClicked : Action
    data class ThemePreferenceSelected(val preference: ThemePreference) : Action
}

sealed interface SideEffect

sealed interface NavigationEffect : SideEffect { data object Back : NavigationEffect }
