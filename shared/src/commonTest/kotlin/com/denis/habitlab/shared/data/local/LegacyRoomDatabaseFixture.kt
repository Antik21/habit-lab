package com.denis.habitlab.shared.data.local

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL

/** Target fixtures use the same bundled SQLite engine as Room to exercise the real v1→v2 path. */
internal fun createV1DatabaseWithLegacyRows(path: String) {
    val connection = BundledSQLiteDriver().open(path)
    try {
        listOf(
            "CREATE TABLE IF NOT EXISTS `experiments` (" +
                "`id` TEXT NOT NULL, `display_name` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                "`active_slot` INTEGER, `created_utc_millis` INTEGER NOT NULL, " +
                "`created_offset_seconds` INTEGER NOT NULL, `created_local_date` TEXT NOT NULL, " +
                "`updated_utc_millis` INTEGER NOT NULL, `updated_offset_seconds` INTEGER NOT NULL, " +
                "`updated_local_date` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_experiments_active_slot` " +
                "ON `experiments` (`active_slot`)",
            "CREATE TABLE IF NOT EXISTS `daily_check_ins` (" +
                "`experiment_id` TEXT NOT NULL, `check_in_local_date` TEXT NOT NULL, " +
                "`outcome` TEXT NOT NULL, `occurred_utc_millis` INTEGER, " +
                "`occurred_offset_seconds` INTEGER, `recorded_utc_millis` INTEGER NOT NULL, " +
                "`recorded_offset_seconds` INTEGER NOT NULL, `recorded_local_date` TEXT NOT NULL, " +
                "PRIMARY KEY(`experiment_id`, `check_in_local_date`), " +
                "FOREIGN KEY(`experiment_id`) REFERENCES `experiments`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE INDEX IF NOT EXISTS `index_daily_check_ins_experiment_id` " +
                "ON `daily_check_ins` (`experiment_id`)",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                "VALUES(42, 'a048e27de35c0f74a2160068c2cd602f')",
            "INSERT INTO experiments VALUES(" +
                "'draft-legacyv1', 'Legacy experiment', 'DRAFT', NULL, 1767225600000, 0, '2026-01-01', " +
                "1767225600000, 0, '2026-01-01')",
            "INSERT INTO daily_check_ins VALUES(" +
                "'draft-legacyv1', '2026-01-02', 'SKIPPED', NULL, NULL, 1767340800000, 0, '2026-01-02')",
            "PRAGMA user_version = 1",
        ).forEach(connection::execSQL)
    } finally {
        connection.close()
    }
}
