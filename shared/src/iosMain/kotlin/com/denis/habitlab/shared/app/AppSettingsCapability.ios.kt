package com.denis.habitlab.shared.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

@Composable
actual fun rememberAppSettingsCapability(): AppSettingsCapability = remember {
    IosAppSettingsCapability
}

private object IosAppSettingsCapability : AppSettingsCapability {
    override fun openApplicationSettings() {
        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let(UIApplication.sharedApplication::openURL)
    }
}
