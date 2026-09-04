package com.denis.habitlab.shared.data.local

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** The bundled Native coroutines artifact does not expose a public IO dispatcher. */
internal actual val roomQueryDispatcher: CoroutineDispatcher = Dispatchers.Default
