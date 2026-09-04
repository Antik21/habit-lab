package com.denis.habitlab.shared.data.local

import android.content.Context
import androidx.room3.Room

/** Android host supplies application context; the shared builder owns driver and dispatcher policy. */
fun createHabitLabDatabase(context: Context): HabitLabDatabase {
    val applicationContext = context.applicationContext
    val databaseFile = applicationContext.getDatabasePath(DATABASE_FILE_NAME)
    return buildHabitLabDatabase(
        Room.databaseBuilder<HabitLabDatabase>(
            context = applicationContext,
            name = databaseFile.absolutePath,
        ),
    )
}

private const val DATABASE_FILE_NAME = "habit-lab.db"
