package com.denis.habitlab.shared.presentation.settings

import com.denis.habitlab.shared.domain.model.ThemePreference

class SettingsUiMapper {
    fun map(preference: ThemePreference): ViewState = ViewState(themePreference = preference)
}
