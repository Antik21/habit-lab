package com.denis.habitlab.shared.data.local

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineDispatcher

@Database(
    entities = [
        ExperimentEntity::class,
        CheckInEntity::class,
        OnboardingCatalogEntryEntity::class,
        OnboardingStateEntity::class,
        OnboardingProtocolEntity::class,
        OnboardingProtocolConfigurationEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@ConstructedBy(HabitLabDatabaseConstructor::class)
abstract class HabitLabDatabase : RoomDatabase() {
    internal abstract fun experimentDao(): ExperimentDao

    internal abstract fun onboardingDao(): OnboardingDao
}

@Suppress("KotlinNoActualForExpect")
expect object HabitLabDatabaseConstructor : RoomDatabaseConstructor<HabitLabDatabase> {
    override fun initialize(): HabitLabDatabase
}

/** Platform dispatchers expose IO where that target supports it publicly. */
internal expect val roomQueryDispatcher: CoroutineDispatcher

/** Common builder policy; platform source sets provide only a sandboxed absolute database path. */
internal fun buildHabitLabDatabase(
    builder: RoomDatabase.Builder<HabitLabDatabase>,
): HabitLabDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(roomQueryDispatcher)
    .addMigrations(ONBOARDING_MIGRATION_1_2)
    .addCallback(OnboardingCatalogSeedCallback())
    .build()
