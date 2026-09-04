package com.denis.habitlab.shared.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The sole app-composition boundary that resolves entry-scoped ViewModels. The Nav3 entry
 * decorator supplies the owning ViewModelStore before this is called; feature ViewModels never
 * depend on Koin or a navigator.
 */
@Composable
internal inline fun <reified ViewModelType : ViewModel> navigationEntryViewModel(
    key: String,
    vararg parameters: Any,
): ViewModelType = koinViewModel(
    key = key,
    parameters = { parametersOf(*parameters) },
)
