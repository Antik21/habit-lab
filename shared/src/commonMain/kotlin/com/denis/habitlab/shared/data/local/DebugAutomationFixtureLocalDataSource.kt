package com.denis.habitlab.shared.data.local

/** Narrow Room boundary used only by the debug automation control. */
internal class DebugAutomationFixtureLocalDataSource(database: HabitLabDatabase) {
    private val dao = database.debugAutomationFixtureDao()

    suspend fun seedIfNoExperiments(): Boolean = dao.seedIfNoExperiments(DebugSeed.fixed)

    suspend fun resetAndSeed() {
        dao.replaceAllWithSeed(DebugSeed.fixed)
    }
}
