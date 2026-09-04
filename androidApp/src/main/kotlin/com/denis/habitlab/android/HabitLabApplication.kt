package com.denis.habitlab.android

import android.app.Application
import com.denis.habitlab.shared.core.platform.PlatformDescriptor
import com.denis.habitlab.shared.data.local.DebugExperimentDatabaseControl
import com.denis.habitlab.shared.data.local.createHabitLabDatabase
import com.denis.habitlab.shared.di.HabitLabRuntime
import com.denis.habitlab.shared.di.initHabitLabRuntime
import com.denis.habitlab.shared.presentation.AppPresenter
import org.koin.android.ext.koin.androidContext

class HabitLabApplication : Application() {
    lateinit var appPresenter: AppPresenter
        private set

    /** Non-null only in debug builds; QA can call this explicit host-owned control to reset fixtures. */
    var debugDatabaseControl: DebugExperimentDatabaseControl? = null
        private set

    override fun onCreate() {
        super.onCreate()
        val runtime: HabitLabRuntime = initHabitLabRuntime(
            platformDescriptor = AndroidPlatformDescriptor,
            database = createHabitLabDatabase(applicationContext),
            isDebugBuild = BuildConfig.DEBUG,
        ) {
            androidContext(this@HabitLabApplication)
        }
        appPresenter = runtime.presenter
        debugDatabaseControl = runtime.debugDatabaseControl
    }
}

private object AndroidPlatformDescriptor : PlatformDescriptor {
    override val name: String = "Android"
}
