package com.denis.habitlab.android

import android.app.Application
import com.denis.habitlab.shared.core.platform.PlatformDescriptor
import com.denis.habitlab.shared.di.initHabitLabKoin
import com.denis.habitlab.shared.presentation.AppPresenter
import org.koin.android.ext.koin.androidContext

class HabitLabApplication : Application() {
    lateinit var appPresenter: AppPresenter
        private set

    override fun onCreate() {
        super.onCreate()
        appPresenter = initHabitLabKoin(platformDescriptor = AndroidPlatformDescriptor) {
            androidContext(this@HabitLabApplication)
        }
    }
}

private object AndroidPlatformDescriptor : PlatformDescriptor {
    override val name: String = "Android"
}
