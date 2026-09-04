package com.denis.habitlab.shared.app

import androidx.compose.runtime.Composable

/**
 * Opens the app's platform settings without changing the app-owned navigation stack. Platform
 * handles stay behind this capability, so routes and dialog results remain common serializable data.
 */
interface AppSettingsCapability {
    fun openApplicationSettings()
}

@Composable
expect fun rememberAppSettingsCapability(): AppSettingsCapability
