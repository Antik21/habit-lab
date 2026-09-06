package com.denis.habitlab.shared.data.repository

import com.denis.habitlab.shared.data.local.AppendConfigurationInsert
import com.denis.habitlab.shared.data.local.InitialProtocolInsert
import com.denis.habitlab.shared.data.local.OnboardingStateWrite
import com.denis.habitlab.shared.data.local.RoomOnboardingLocalDataSource
import com.denis.habitlab.shared.data.mapper.OnboardingDecode
import com.denis.habitlab.shared.data.mapper.toDomain
import com.denis.habitlab.shared.data.mapper.toDomainOnboardingState
import com.denis.habitlab.shared.domain.model.ActiveOnboardingProtocol
import com.denis.habitlab.shared.domain.model.ConfirmedContextSelection
import com.denis.habitlab.shared.domain.model.GoalId
import com.denis.habitlab.shared.domain.model.OnboardingHealthState
import com.denis.habitlab.shared.domain.model.OnboardingProtocolId
import com.denis.habitlab.shared.domain.model.ProtocolTemplateId
import com.denis.habitlab.shared.domain.model.SetupDraftReference
import com.denis.habitlab.shared.domain.repository.ActiveProtocolWriteResult
import com.denis.habitlab.shared.domain.repository.OnboardingRepository
import com.denis.habitlab.shared.domain.repository.OnboardingStorageFailure
import com.denis.habitlab.shared.domain.repository.OnboardingStorageOperation
import com.denis.habitlab.shared.domain.repository.OnboardingWriteResult
import kotlinx.coroutines.CancellationException

internal class RoomOnboardingRepository(
    private val localDataSource: RoomOnboardingLocalDataSource,
) : OnboardingRepository {
    override suspend fun confirmEligibility(): OnboardingWriteResult = save(OnboardingStorageOperation.CONFIRM_ELIGIBILITY) {
        localDataSource.confirmEligibility()
    }

    override suspend fun revokeEligibility(): OnboardingWriteResult = save(OnboardingStorageOperation.REVOKE_ELIGIBILITY) {
        localDataSource.revokeEligibility()
    }

    override suspend fun confirmGoal(goal: GoalId): OnboardingWriteResult = save(OnboardingStorageOperation.CONFIRM_GOAL) {
        localDataSource.confirmGoal(goal)
    }

    override suspend fun confirmContexts(contexts: ConfirmedContextSelection): OnboardingWriteResult =
        save(OnboardingStorageOperation.CONFIRM_CONTEXTS) {
            localDataSource.confirmContexts(contexts)
        }

    override suspend fun selectProtocol(template: ProtocolTemplateId): OnboardingWriteResult =
        save(OnboardingStorageOperation.SELECT_PROTOCOL) {
            localDataSource.selectProtocol(template)
        }

    override suspend fun saveHealthState(health: OnboardingHealthState): OnboardingWriteResult =
        save(OnboardingStorageOperation.SAVE_HEALTH_STATE) {
            localDataSource.saveHealthState(health)
        }

    override suspend fun saveSetupDraftReference(reference: SetupDraftReference): OnboardingWriteResult =
        save(OnboardingStorageOperation.SAVE_SETUP_DRAFT_REFERENCE) {
            localDataSource.saveSetupDraftReference(reference)
        }

    override suspend fun createInitialActiveProtocol(
        protocolId: OnboardingProtocolId,
        sourceSetupDraft: SetupDraftReference,
    ): ActiveProtocolWriteResult = try {
        when (val result = localDataSource.createInitialActiveProtocol(protocolId, sourceSetupDraft)) {
            is InitialProtocolInsert.Created -> ActiveProtocolWriteResult.Created(
                result.protocol.toDomain(result.configuration).decodeOrThrow(),
            )

            is InitialProtocolInsert.Unchanged -> ActiveProtocolWriteResult.Unchanged(
                result.protocol.toDomain(result.configuration).decodeOrThrow(),
            )

            InitialProtocolInsert.ActiveAlreadyExists -> ActiveProtocolWriteResult.ActiveProtocolAlreadyExists
            InitialProtocolInsert.MissingSelectedTemplate -> ActiveProtocolWriteResult.MissingSelectedTemplate
            InitialProtocolInsert.InvalidSelectedTemplate -> ActiveProtocolWriteResult.InvalidSelectedTemplate
            is InitialProtocolInsert.Rejected -> ActiveProtocolWriteResult.Rejected(result.precondition)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        ActiveProtocolWriteResult.Failed(
            OnboardingStorageFailure(OnboardingStorageOperation.CREATE_INITIAL_ACTIVE_PROTOCOL),
        )
    }

    override suspend fun appendProtocolConfiguration(
        protocolId: OnboardingProtocolId,
        sourceSetupDraft: SetupDraftReference,
    ): ActiveProtocolWriteResult = try {
        when (val result = localDataSource.appendConfiguration(protocolId, sourceSetupDraft)) {
            is AppendConfigurationInsert.Appended -> ActiveProtocolWriteResult.Appended(
                result.protocol.toDomain(result.configuration).decodeOrThrow(),
            )

            is AppendConfigurationInsert.Unchanged -> ActiveProtocolWriteResult.Unchanged(
                result.protocol.toDomain(result.configuration).decodeOrThrow(),
            )

            AppendConfigurationInsert.MissingProtocol -> ActiveProtocolWriteResult.MissingProtocol
            is AppendConfigurationInsert.Rejected -> ActiveProtocolWriteResult.Rejected(result.precondition)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        ActiveProtocolWriteResult.Failed(
            OnboardingStorageFailure(OnboardingStorageOperation.APPEND_PROTOCOL_CONFIGURATION),
        )
    }
}

private suspend fun save(
    operation: OnboardingStorageOperation,
    write: suspend () -> OnboardingStateWrite,
): OnboardingWriteResult = try {
    when (val result = write()) {
        is OnboardingStateWrite.Written -> OnboardingWriteResult.Saved(
            result.state.toDomainOnboardingState().decodeOrThrow(),
        )

        is OnboardingStateWrite.Unchanged -> OnboardingWriteResult.Unchanged(
            result.state.toDomainOnboardingState().decodeOrThrow(),
        )

        is OnboardingStateWrite.Rejected -> OnboardingWriteResult.Rejected(result.precondition)
    }
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    OnboardingWriteResult.Failed(OnboardingStorageFailure(operation))
}

private fun <T> OnboardingDecode<T>.decodeOrThrow(): T = when (this) {
    is OnboardingDecode.Valid -> value
    is OnboardingDecode.Invalid -> error("Invalid persisted onboarding ${reason.field}: ${reason.rawValue}")
}
