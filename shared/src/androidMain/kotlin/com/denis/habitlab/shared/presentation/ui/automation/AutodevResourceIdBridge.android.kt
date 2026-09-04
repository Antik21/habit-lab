package com.denis.habitlab.shared.presentation.ui.automation

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

actual fun Modifier.enableAutodevResourceIds(): Modifier = semantics {
    testTagsAsResourceId = true
}
