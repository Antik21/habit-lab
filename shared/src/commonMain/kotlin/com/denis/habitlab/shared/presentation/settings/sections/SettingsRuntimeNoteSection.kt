package com.denis.habitlab.shared.presentation.settings.sections

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.settings_runtime_note
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsRuntimeNoteSection() { Text(stringResource(Res.string.settings_runtime_note)) }

@Preview
@Composable
private fun Preview() { HabitLabTheme { SettingsRuntimeNoteSection() } }
