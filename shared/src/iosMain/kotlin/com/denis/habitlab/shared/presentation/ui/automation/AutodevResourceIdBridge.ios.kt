package com.denis.habitlab.shared.presentation.ui.automation

import androidx.compose.ui.Modifier

/** Compose 1.12 maps test tags to iOS accessibility identifiers without extra configuration. */
actual fun Modifier.enableAutodevResourceIds(): Modifier = this
