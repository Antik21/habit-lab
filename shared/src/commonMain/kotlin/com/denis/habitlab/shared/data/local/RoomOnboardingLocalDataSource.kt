package com.denis.habitlab.shared.data.local

import com.denis.habitlab.shared.domain.model.ConfirmedContextSelection
import com.denis.habitlab.shared.domain.model.GoalId
import com.denis.habitlab.shared.domain.model.OnboardingHealthState
import com.denis.habitlab.shared.domain.model.OnboardingProtocolId
import com.denis.habitlab.shared.domain.model.ProtocolTemplateId
import com.denis.habitlab.shared.domain.model.SetupDraftReference
import com.denis.habitlab.shared.data.mapper.toPersisted
import kotlinx.coroutines.flow.Flow

/** Room-only onboarding boundary. It exposes only entities and primitive storage inputs. */
internal class RoomOnboardingLocalDataSource(database: HabitLabDatabase) {
    private val dao = database.onboardingDao()

    fun observeCatalogEntries(): Flow<List<OnboardingCatalogEntryEntity>> = dao.observeCatalogEntries()

    fun observeState(): Flow<OnboardingStateEntity?> = dao.observeState()

    fun observeActiveProtocol(): Flow<ActiveOnboardingProtocolSnapshot?> = dao.observeActiveProtocol()

    suspend fun confirmEligibility(): OnboardingStateWrite = dao.confirmEligibility()

    suspend fun revokeEligibility(): OnboardingStateWrite = dao.revokeEligibility()

    suspend fun confirmGoal(goal: GoalId): OnboardingStateWrite = dao.confirmGoal(goal.persistedValue)

    suspend fun confirmContexts(contexts: ConfirmedContextSelection): OnboardingStateWrite = dao.confirmContexts(
        contexts.toPersistedContextIds(),
    )

    suspend fun selectProtocol(template: ProtocolTemplateId): OnboardingStateWrite =
        dao.selectProtocol(template.persistedValue)

    suspend fun saveHealthState(health: OnboardingHealthState): OnboardingStateWrite =
        dao.saveHealthState(health.toPersisted())

    suspend fun saveSetupDraftReference(reference: SetupDraftReference): OnboardingStateWrite =
        dao.saveSetupDraftReference(reference.toPersisted())

    suspend fun createInitialActiveProtocol(
        protocolId: OnboardingProtocolId,
        sourceDraft: SetupDraftReference,
    ): InitialProtocolInsert = dao.createInitialActiveProtocol(
        protocolId = protocolId.value,
        sourceDraft = sourceDraft.toPersisted(),
    )

    suspend fun appendConfiguration(
        protocolId: OnboardingProtocolId,
        sourceDraft: SetupDraftReference,
    ): AppendConfigurationInsert = dao.appendConfiguration(protocolId.value, sourceDraft.toPersisted())

}

private fun ConfirmedContextSelection.toPersistedContextIds(): String = when (this) {
    ConfirmedContextSelection.ExplicitlyEmpty -> ""
    is ConfirmedContextSelection.Selected -> values
        .sortedBy { it.ordinal }
        .joinToString(separator = ",") { it.persistedValue }
}
