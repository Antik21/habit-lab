package com.denis.habitlab.shared.data.repository

import com.denis.habitlab.shared.domain.interactor.ObserveThemePreference
import com.denis.habitlab.shared.domain.interactor.SetThemePreference
import com.denis.habitlab.shared.domain.model.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeAppPreferenceRepositoryTest {
    @Test
    fun themePreferenceIsObservableImmediatelyAcrossUseCasesAndStartsAtSystem() {
        val repository = RuntimeAppPreferenceRepository()
        val observe = ObserveThemePreference(repository)
        val set = SetThemePreference(repository)

        assertEquals(ThemePreference.SYSTEM, observe().value)

        set(ThemePreference.DARK)

        assertEquals(ThemePreference.DARK, observe().value)
        assertEquals(ThemePreference.DARK, repository.themePreference.value)
    }
}
