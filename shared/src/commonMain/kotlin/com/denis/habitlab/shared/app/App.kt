package com.denis.habitlab.shared.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.denis.habitlab.shared.presentation.AppPresenter

@Composable
fun App(presenter: AppPresenter) {
    val appUiModel = remember(presenter) { presenter.present() }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = appUiModel.title)
            }
        }
    }
}
