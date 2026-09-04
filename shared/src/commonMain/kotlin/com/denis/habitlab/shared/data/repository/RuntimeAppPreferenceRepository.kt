package com.denis.habitlab.shared.data.repository

import com.denis.habitlab.shared.domain.model.ThemePreference
import com.denis.habitlab.shared.domain.repository.AppPreferenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One process-owned preference store. It intentionally has no disk backing in DEN-12: settings
 * remain consistent across all active common entries and theme changes take effect immediately.
 */
internal class RuntimeAppPreferenceRepository : AppPreferenceRepository {
    private val mutableThemePreference = MutableStateFlow(ThemePreference.SYSTEM)

    override val themePreference: StateFlow<ThemePreference> = mutableThemePreference.asStateFlow()

    override fun setThemePreference(preference: ThemePreference) {
        mutableThemePreference.value = preference
    }
}
