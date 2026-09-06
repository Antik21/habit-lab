package com.denis.habitlab.shared.data.local

import androidx.room3.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSUUID
import platform.Foundation.NSTemporaryDirectory

@OptIn(ExperimentalForeignApi::class)
internal actual class FileBackedRoomDatabaseFixture actual constructor() {
    private val databasePath = "${NSTemporaryDirectory()}room-experiment-store-reopen-${NSUUID().UUIDString}.db"

    actual fun open(): HabitLabDatabase = buildHabitLabDatabase(
        Room.databaseBuilder<HabitLabDatabase>(name = databasePath),
    )

    actual fun createV1DatabaseWithLegacyRows() {
        createV1DatabaseWithLegacyRows(databasePath)
    }

    actual fun delete() {
        val fileManager = NSFileManager.defaultManager
        listOf("", "-shm", "-wal", ".lck").forEach { suffix ->
            fileManager.removeItemAtPath("$databasePath$suffix", error = null)
        }
    }
}
