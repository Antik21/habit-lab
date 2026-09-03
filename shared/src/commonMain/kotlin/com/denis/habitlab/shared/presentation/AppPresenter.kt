package com.denis.habitlab.shared.presentation

import com.denis.habitlab.shared.domain.interactor.GetAppMetadata
import com.denis.habitlab.shared.domain.model.AppMetadata
import com.denis.habitlab.shared.presentation.model.AppUiModel

class AppPresenter(
    private val getAppMetadata: GetAppMetadata,
) {
    fun present(): AppUiModel = getAppMetadata().toUiModel()
}

private fun AppMetadata.toUiModel(): AppUiModel = AppUiModel(
    title = name,
    subtitle = platformName,
)
