package com.denis.habitlab.shared.domain.interactor

import com.denis.habitlab.shared.domain.model.ThemePreference
import com.denis.habitlab.shared.domain.repository.AppPreferenceRepository
import kotlinx.coroutines.flow.StateFlow

class ObserveThemePreference(
    private val repository: AppPreferenceRepository,
) {
    operator fun invoke(): StateFlow<ThemePreference> = repository.themePreference
}

class SetThemePreference(
    private val repository: AppPreferenceRepository,
) {
    operator fun invoke(preference: ThemePreference) {
        repository.setThemePreference(preference)
    }
}
