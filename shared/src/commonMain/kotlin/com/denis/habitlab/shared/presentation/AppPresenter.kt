package com.denis.habitlab.shared.presentation

import com.denis.habitlab.shared.domain.interactor.GetAppMetadata
import com.denis.habitlab.shared.domain.interactor.ObserveThemePreference
import com.denis.habitlab.shared.domain.model.AppMetadata
import com.denis.habitlab.shared.domain.model.ThemePreference
import com.denis.habitlab.shared.presentation.model.AppUiModel
import kotlinx.coroutines.flow.StateFlow

class AppPresenter(
    private val getAppMetadata: GetAppMetadata,
    observeThemePreference: ObserveThemePreference,
) {
    val themePreference: StateFlow<ThemePreference> = observeThemePreference()

    fun present(): AppUiModel = getAppMetadata().toUiModel()
}

private fun AppMetadata.toUiModel(): AppUiModel = AppUiModel(
    title = name,
    subtitle = platformName,
)
