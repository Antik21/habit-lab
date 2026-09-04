package com.denis.habitlab.shared.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denis.habitlab.shared.domain.interactor.ObserveThemePreference
import com.denis.habitlab.shared.domain.interactor.SetThemePreference
import kotlinx.coroutines.flow.collect
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

class SettingsViewModel(
    observeThemePreference: ObserveThemePreference,
    private val setThemePreference: SetThemePreference,
    private val uiMapper: SettingsUiMapper,
) : ViewModel(), ContainerHost<ViewState, SideEffect> {
    override val container: Container<ViewState, SideEffect> = viewModelScope.container(
        initialState = ViewState(),
        onCreate = { observeThemePreference().collect { preference -> reduce { uiMapper.map(preference) } } },
    )

    fun dispatchAction(action: Action) {
        when (action) {
            Action.BackClicked -> intent { postSideEffect(NavigationEffect.Back) }
            is Action.ThemePreferenceSelected -> intent { setThemePreference(action.preference) }
        }
    }
}
