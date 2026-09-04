package com.denis.habitlab.shared.data.local

import androidx.room3.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * The dedicated sandbox directory is configured before Room opens the database. Its child database
 * and SQLite WAL/SHM files inherit the backup exclusion and file-protection policy.
 */
fun createHabitLabDatabase(): HabitLabDatabase = buildHabitLabDatabase(
    Room.databaseBuilder<HabitLabDatabase>(name = databasePath()),
)

@OptIn(ExperimentalForeignApi::class)
private fun databasePath(): String {
    val fileManager = NSFileManager.defaultManager
    val applicationSupport = requireNotNull(
        fileManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ),
    ) { "Could not resolve the Application Support directory" }
    val databaseDirectory = requireNotNull(applicationSupport.URLByAppendingPathComponent(
        pathComponent = DATABASE_DIRECTORY_NAME,
        isDirectory = true,
    )) { "Could not resolve the HabitLab database directory" }
    check(
        fileManager.createDirectoryAtURL(
            url = databaseDirectory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        ),
    ) { "Could not create the HabitLab database directory" }
    check(
        fileManager.setAttributes(
            attributes = mapOf(
                NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication,
            ),
            ofItemAtPath = requireNotNull(databaseDirectory.path),
            error = null,
        ),
    ) { "Could not set HabitLab database file protection" }
    check(
        databaseDirectory.setResourceValue(
            value = true,
            forKey = NSURLIsExcludedFromBackupKey,
            error = null,
        ),
    ) { "Could not exclude the HabitLab database directory from backup" }
    return "${requireNotNull(databaseDirectory.path)}/$DATABASE_FILE_NAME"
}

private const val DATABASE_DIRECTORY_NAME = "HabitLab"
private const val DATABASE_FILE_NAME = "habit-lab.db"
