package com.denis.habitlab.shared.presentation.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.domain.model.ThemePreference
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import com.denis.habitlab.shared.presentation.ui.automation.autodevId
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.settings_theme_dark
import habitlab.shared.generated.resources.settings_theme_light
import habitlab.shared.generated.resources.settings_theme_system
import habitlab.shared.generated.resources.settings_theme_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsThemeSection(
    selected: ThemePreference,
    onSelected: (ThemePreference) -> Unit,
) {
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Small),
    ) {
        Text(stringResource(Res.string.settings_theme_title))
        ThemeChoice(
            ThemePreference.SYSTEM, selected, Res.string.settings_theme_system,
            AutomationId.SettingsThemeSystem, onSelected,
        )
        ThemeChoice(
            ThemePreference.LIGHT, selected, Res.string.settings_theme_light,
            AutomationId.SettingsThemeLight, onSelected,
        )
        ThemeChoice(
            ThemePreference.DARK, selected, Res.string.settings_theme_dark,
            AutomationId.SettingsThemeDark, onSelected,
        )
    }
}

@Composable
private fun ThemeChoice(
    preference: ThemePreference,
    selected: ThemePreference,
    labelResource: org.jetbrains.compose.resources.StringResource,
    automationId: AutomationId,
    onSelected: (ThemePreference) -> Unit,
) {
    val isSelected = selected == preference
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .autodevId(automationId)
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = { onSelected(preference) },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Text(stringResource(labelResource))
    }
}

@Preview
@Composable
private fun Preview() { HabitLabTheme { SettingsThemeSection(ThemePreference.SYSTEM, {}) } }
