package com.denis.habitlab.shared.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberAppSettingsCapability(): AppSettingsCapability {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidAppSettingsCapability(context) }
}

private class AndroidAppSettingsCapability(
    private val context: Context,
) : AppSettingsCapability {
    override fun openApplicationSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
