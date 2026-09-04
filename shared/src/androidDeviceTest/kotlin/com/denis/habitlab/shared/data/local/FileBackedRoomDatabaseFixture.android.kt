package com.denis.habitlab.shared.data.local

import androidx.room3.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID

internal actual class FileBackedRoomDatabaseFixture actual constructor() {
    private val databaseFile = InstrumentationRegistry.getInstrumentation()
        .targetContext
        .getDatabasePath("room-experiment-store-reopen-${UUID.randomUUID()}.db")

    actual fun open(): HabitLabDatabase = buildHabitLabDatabase(
        Room.databaseBuilder(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            name = databaseFile.absolutePath,
        ),
    )

    actual fun delete() {
        databaseFile.deleteWithSidecars()
    }

    private fun File.deleteWithSidecars() {
        listOf("", "-shm", "-wal", ".lck").forEach { suffix ->
            File("$absolutePath$suffix").delete()
        }
    }
}
