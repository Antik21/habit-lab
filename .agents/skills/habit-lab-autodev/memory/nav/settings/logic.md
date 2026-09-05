# Settings logic

[`SettingsViewModel`](../../../../../../shared/src/commonMain/kotlin/com/denis/habitlab/shared/presentation/settings/SettingsViewModel.kt) observes the theme preference, maps it through [`SettingsUiMapper`](../../../../../../shared/src/commonMain/kotlin/com/denis/habitlab/shared/presentation/settings/SettingsUiMapper.kt), and delegates a selected theme to `SetThemePreference`. Its only navigation effect is `Back`.

[`AppNavigator.handleSettingsEffect`](../../../../../../shared/src/commonMain/kotlin/com/denis/habitlab/shared/app/Navigation3AppHost.kt) accepts that effect only while Settings is topmost, then returns to Gallery. Platform system/edge back reaches the same navigator back path.
