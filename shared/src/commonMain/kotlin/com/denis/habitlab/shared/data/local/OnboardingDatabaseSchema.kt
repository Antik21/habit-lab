package com.denis.habitlab.shared.data.local

import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Explicit, non-destructive v1→v2 migration shared by Android, iOS, and test builders. */
internal val ONBOARDING_MIGRATION_1_2: Migration = Migration(1, 2) { connection ->
    OnboardingDatabaseSchema.createV2Tables(connection)
    OnboardingDatabaseSchema.seedReleaseCatalog(connection)
    OnboardingDatabaseSchema.ensureInitialState(connection)
}

/** Fresh v2 databases receive the same idempotent release catalog as migrated databases. */
internal class OnboardingCatalogSeedCallback : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) {
        OnboardingDatabaseSchema.seedReleaseCatalog(connection)
        OnboardingDatabaseSchema.ensureInitialState(connection)
    }
}

internal object OnboardingDatabaseSchema {
    fun createV2Tables(connection: SQLiteConnection) {
        createTableStatements.forEach(connection::execSQL)
        createIndexStatements.forEach(connection::execSQL)
    }

    fun seedReleaseCatalog(connection: SQLiteConnection) {
        catalogInsertStatements.forEach(connection::execSQL)
    }

    fun ensureInitialState(connection: SQLiteConnection) {
        connection.execSQL(
            "INSERT OR IGNORE INTO onboarding_state (" +
                "singleton_id, eligibility, progress_kind, progress_step, goal_id, " +
                "contexts_confirmed, contexts_require_confirmation, context_ids, template_id, " +
                "has_health_state, health_capability_id, health_capability_value, " +
                "health_provider_availability, health_access_outcome, health_visible_records, " +
                "health_coverage, health_freshness, health_suitability, manual_plan_state, " +
                "setup_draft_attempt_id, setup_draft_revision" +
                ") VALUES (1, 'UNCONFIRMED', 'NOT_STARTED', NULL, NULL, 0, 0, '', NULL, " +
                "0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)",
        )
    }

    private val createTableStatements = listOf(
        "CREATE TABLE IF NOT EXISTS `onboarding_catalog_entries` (" +
            "`catalog_type` TEXT NOT NULL, `catalog_id` TEXT NOT NULL, `display_name` TEXT NOT NULL, " +
            "`sort_position` INTEGER NOT NULL, `manual_capable` INTEGER NOT NULL, " +
            "PRIMARY KEY(`catalog_type`, `catalog_id`))",
        "CREATE TABLE IF NOT EXISTS `onboarding_state` (" +
            "`singleton_id` INTEGER NOT NULL, `eligibility` TEXT NOT NULL, `progress_kind` TEXT NOT NULL, " +
            "`progress_step` TEXT, `goal_id` TEXT, `contexts_confirmed` INTEGER NOT NULL, " +
            "`contexts_require_confirmation` INTEGER NOT NULL, `context_ids` TEXT NOT NULL, " +
            "`template_id` TEXT, `has_health_state` INTEGER NOT NULL, `health_capability_id` TEXT, " +
            "`health_capability_value` TEXT, `health_provider_availability` TEXT, " +
            "`health_access_outcome` TEXT, `health_visible_records` TEXT, `health_coverage` TEXT, " +
            "`health_freshness` TEXT, `health_suitability` TEXT, `manual_plan_state` TEXT, " +
            "`setup_draft_attempt_id` TEXT, `setup_draft_revision` INTEGER, PRIMARY KEY(`singleton_id`))",
        "CREATE TABLE IF NOT EXISTS `onboarding_protocols` (" +
            "`id` TEXT NOT NULL, `template_id` TEXT NOT NULL, `status` TEXT NOT NULL, " +
            "`active_slot` INTEGER, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `onboarding_protocol_configurations` (" +
            "`protocol_id` TEXT NOT NULL, `version` INTEGER NOT NULL, `source_setup_draft_id` TEXT NOT NULL, " +
            "`source_setup_draft_revision` INTEGER NOT NULL, PRIMARY KEY(`protocol_id`, `version`), " +
            "FOREIGN KEY(`protocol_id`) REFERENCES `onboarding_protocols`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE)",
    )

    private val createIndexStatements = listOf(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_onboarding_catalog_entries_catalog_type_sort_position` " +
            "ON `onboarding_catalog_entries` (`catalog_type`, `sort_position`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_onboarding_protocols_active_slot` " +
            "ON `onboarding_protocols` (`active_slot`)",
        "CREATE INDEX IF NOT EXISTS `index_onboarding_protocol_configurations_protocol_id` " +
            "ON `onboarding_protocol_configurations` (`protocol_id`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS " +
            "`index_onboarding_protocol_configurations_source_setup_draft_id_source_setup_draft_revision` " +
            "ON `onboarding_protocol_configurations` (`source_setup_draft_id`, `source_setup_draft_revision`)",
    )

    /** Ordered, idempotent catalog seeding. It intentionally has exactly three manual templates. */
    private val catalogInsertStatements = listOf(
        catalogInsert("GOAL", "sleep-better", "Лучше спать", 1),
        catalogInsert("GOAL", "wake-refreshed", "Бодрее просыпаться", 2),
        catalogInsert("GOAL", "morning-energy", "Больше энергии утром", 3),
        catalogInsert("GOAL", "calm-evening", "Спокойнее проводить вечер", 4),
        catalogInsert("GOAL", "daily-movement", "Больше двигаться каждый день", 5),
        catalogInsert("CONTEXT", "low-evening-movement", "Мало движения вечером", 1),
        catalogInsert("CONTEXT", "screen-before-sleep", "Экран или соцсети перед сном", 2),
        catalogInsert("CONTEXT", "irregular-sleep-time", "Нерегулярное время сна", 3),
        catalogInsert("CONTEXT", "late-meal", "Поздние приёмы пищи", 4),
        catalogInsert("CONTEXT", "hard-to-unwind", "Сложно расслабиться вечером", 5),
        catalogInsert("CONTEXT", "variable-schedule", "График часто меняется", 6),
        catalogInsert("CONTEXT", "not-sure-yet", "Пока не знаю", 7),
        catalogInsert("TEMPLATE", "after-dinner-walk", "Прогулка после ужина", 1, manualCapable = true),
        catalogInsert("TEMPLATE", "calm-evening-ritual", "Спокойный вечерний ритуал", 2, manualCapable = true),
        catalogInsert("TEMPLATE", "regular-sleep-schedule", "Регулярное время сна", 3, manualCapable = true),
        catalogInsert("METRIC", "sleep-duration", "Длительность сна", 1),
        catalogInsert("METRIC", "sleep-session-duration", "Длительность сессии сна", 2),
        catalogInsert("METRIC", "morning-energy", "Утренняя энергия", 3),
        catalogInsert("METRIC", "subjective-sleep-quality", "Субъективное качество сна", 4),
        catalogInsert("METRIC", "sleep-timing-variability", "Вариативность времени сна", 5),
        catalogInsert("METRIC", "sleep-attempt-time", "Время попытки заснуть", 6),
    )

    private fun catalogInsert(
        type: String,
        id: String,
        name: String,
        position: Int,
        manualCapable: Boolean = false,
    ): String = "INSERT OR IGNORE INTO onboarding_catalog_entries " +
        "(catalog_type, catalog_id, display_name, sort_position, manual_capable) VALUES " +
        "('$type', '$id', '$name', $position, ${if (manualCapable) 1 else 0})"
}
