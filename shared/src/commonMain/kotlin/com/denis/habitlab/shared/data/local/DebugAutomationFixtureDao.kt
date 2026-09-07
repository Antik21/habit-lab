package com.denis.habitlab.shared.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction

/**
 * Debug-runtime-only fixture transaction. It owns the reference experiments and the exact
 * completed onboarding proof together, so the launch gate never observes a half-seeded fixture.
 */
@Dao
internal interface DebugAutomationFixtureDao {
    @Query("SELECT COUNT(*) FROM experiments")
    suspend fun experimentCount(): Int

    @Query("DELETE FROM daily_check_ins")
    suspend fun deleteAllCheckIns()

    @Query("DELETE FROM experiments")
    suspend fun deleteAllExperiments()

    @Query("DELETE FROM onboarding_protocol_configurations")
    suspend fun deleteAllOnboardingConfigurations()

    @Query("DELETE FROM onboarding_protocols")
    suspend fun deleteAllOnboardingProtocols()

    @Query("DELETE FROM onboarding_state")
    suspend fun deleteAllOnboardingStates()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExperiments(entities: List<ExperimentEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCheckIns(entities: List<CheckInEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOnboardingState(entity: OnboardingStateEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOnboardingProtocol(entity: OnboardingProtocolEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOnboardingConfiguration(entity: OnboardingProtocolConfigurationEntity)

    @Transaction
    suspend fun seedIfNoExperiments(seed: DebugSeed): Boolean {
        if (experimentCount() != 0) return false
        replaceAllWithSeed(seed)
        return true
    }

    @Transaction
    suspend fun replaceAllWithSeed(seed: DebugSeed) {
        deleteAllCheckIns()
        deleteAllExperiments()
        deleteAllOnboardingConfigurations()
        deleteAllOnboardingProtocols()
        deleteAllOnboardingStates()
        insertExperiments(seed.experiments)
        insertCheckIns(seed.checkIns)
        insertOnboardingState(seed.onboardingState)
        insertOnboardingProtocol(seed.activeOnboardingProtocol)
        insertOnboardingConfiguration(seed.activeOnboardingConfiguration)
    }
}
