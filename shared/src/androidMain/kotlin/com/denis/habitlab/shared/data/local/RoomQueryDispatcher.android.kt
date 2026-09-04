package com.denis.habitlab.shared.data.local

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val roomQueryDispatcher: CoroutineDispatcher = Dispatchers.IO
