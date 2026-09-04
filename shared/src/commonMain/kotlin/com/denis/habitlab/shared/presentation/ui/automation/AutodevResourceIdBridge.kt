package com.denis.habitlab.shared.presentation.ui.automation

import androidx.compose.ui.Modifier

/**
 * Enables the platform-specific mapping needed by legacy Android UiAutomator selectors on the
 * root of an automation subtree. Product composables stay platform-neutral.
 */
expect fun Modifier.enableAutodevResourceIds(): Modifier
