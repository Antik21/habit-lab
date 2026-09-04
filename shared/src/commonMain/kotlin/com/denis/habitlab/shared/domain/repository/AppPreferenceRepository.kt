package com.denis.habitlab.shared.domain.repository

import com.denis.habitlab.shared.domain.model.ThemePreference
import kotlinx.coroutines.flow.StateFlow

/** Focused boundary for common preferences that must be observable outside the Settings entry. */
interface AppPreferenceRepository {
    val themePreference: StateFlow<ThemePreference>

    fun setThemePreference(preference: ThemePreference)
}
