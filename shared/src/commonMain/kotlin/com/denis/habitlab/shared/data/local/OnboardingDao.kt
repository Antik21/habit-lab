package com.denis.habitlab.shared.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.denis.habitlab.shared.domain.model.CatalogIdParseResult
import com.denis.habitlab.shared.domain.model.ProtocolTemplateId
import com.denis.habitlab.shared.domain.repository.OnboardingPrecondition
import kotlinx.coroutines.flow.Flow

@Dao
internal interface OnboardingDao {
    @Query("SELECT * FROM onboarding_catalog_entries ORDER BY catalog_type ASC, sort_position ASC")
    fun observeCatalogEntries(): Flow<List<OnboardingCatalogEntryEntity>>

    @Query("SELECT * FROM onboarding_state WHERE singleton_id = $ONBOARDING_SINGLETON_ID LIMIT 1")
    fun observeState(): Flow<OnboardingStateEntity?>

    @Query("SELECT * FROM onboarding_state WHERE singleton_id = $ONBOARDING_SINGLETON_ID LIMIT 1")
    suspend fun state(): OnboardingStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceState(state: OnboardingStateEntity)

    @Query(
        "SELECT DISTINCT protocols.* FROM onboarding_protocols AS protocols " +
            "LEFT JOIN onboarding_protocol_configurations AS configurations " +
            "ON protocols.id = configurations.protocol_id " +
            "WHERE protocols.active_slot = $ONBOARDING_ACTIVE_SLOT LIMIT 1",
    )
    fun observeActiveProtocol(): Flow<OnboardingProtocolEntity?>

    @Query("SELECT * FROM onboarding_protocols WHERE active_slot = $ONBOARDING_ACTIVE_SLOT LIMIT 1")
    suspend fun activeProtocol(): OnboardingProtocolEntity?

    @Query("SELECT * FROM onboarding_protocols WHERE id = :protocolId LIMIT 1")
    suspend fun protocol(protocolId: String): OnboardingProtocolEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProtocol(protocol: OnboardingProtocolEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConfiguration(configuration: OnboardingProtocolConfigurationEntity)

    @Query(
        "SELECT * FROM onboarding_protocol_configurations " +
            "WHERE protocol_id = :protocolId ORDER BY version DESC LIMIT 1",
    )
    suspend fun latestConfiguration(protocolId: String): OnboardingProtocolConfigurationEntity?

    @Query("DELETE FROM onboarding_protocols")
    suspend fun deleteAllProtocols()

    @Transaction
    suspend fun confirmEligibility(): OnboardingStateWrite {
        val current = state() ?: initialOnboardingStateEntity()
        if (current.isEligibilityConfirmed()) return OnboardingStateWrite.Unchanged(current)
        val updated = current.copy(
            eligibility = ELIGIBILITY_CONFIRMED,
            progressKind = PROGRESS_IN_PROGRESS,
            progressStep = STEP_OUTCOME,
        )
        replaceState(updated)
        return OnboardingStateWrite.Written(updated)
    }

    @Transaction
    suspend fun revokeEligibility(): OnboardingStateWrite {
        deleteAllProtocols()
        val initial = initialOnboardingStateEntity()
        replaceState(initial)
        return OnboardingStateWrite.Written(initial)
    }

    @Transaction
    suspend fun confirmGoal(goalId: String): OnboardingStateWrite {
        val current = state() ?: initialOnboardingStateEntity()
        if (!current.isEligibilityConfirmed()) {
            return OnboardingStateWrite.Rejected(OnboardingPrecondition.ELIGIBILITY_CONFIRMED)
        }
        val changedGoal = current.goalId != goalId
        val updated = current.copy(
            progressKind = PROGRESS_IN_PROGRESS,
            progressStep = STEP_CONTEXT,
            goalId = goalId,
            contextsRequireConfirmation = if (changedGoal && current.contextsConfirmed) {
                true
            } else {
                current.contextsRequireConfirmation
            },
            templateId = if (changedGoal) null else current.templateId,
            hasHealthState = if (changedGoal) false else current.hasHealthState,
            healthCapabilityId = if (changedGoal) null else current.healthCapabilityId,
            healthCapabilityValue = if (changedGoal) null else current.healthCapabilityValue,
            healthProviderAvailability = if (changedGoal) null else current.healthProviderAvailability,
            healthAccessOutcome = if (changedGoal) null else current.healthAccessOutcome,
            healthVisibleRecords = if (changedGoal) null else current.healthVisibleRecords,
            healthCoverage = if (changedGoal) null else current.healthCoverage,
            healthFreshness = if (changedGoal) null else current.healthFreshness,
            healthSuitability = if (changedGoal) null else current.healthSuitability,
            manualPlanState = if (changedGoal) null else current.manualPlanState,
            setupDraftAttemptId = if (changedGoal) null else current.setupDraftAttemptId,
            setupDraftRevision = if (changedGoal) null else current.setupDraftRevision,
        )
        replaceState(updated)
        return OnboardingStateWrite.Written(updated)
    }

    @Transaction
    suspend fun confirmContexts(contextIds: String): OnboardingStateWrite {
        val current = state() ?: initialOnboardingStateEntity()
        current.requireEligibilityAndGoal()?.let { return OnboardingStateWrite.Rejected(it) }
        val updated = current.copy(
            progressKind = PROGRESS_IN_PROGRESS,
            progressStep = STEP_PROTOCOLS,
            contextsConfirmed = true,
            contextsRequireConfirmation = false,
            contextIds = contextIds,
            templateId = null,
            hasHealthState = false,
            healthCapabilityId = null,
            healthCapabilityValue = null,
            healthProviderAvailability = null,
            healthAccessOutcome = null,
            healthVisibleRecords = null,
            healthCoverage = null,
            healthFreshness = null,
            healthSuitability = null,
            manualPlanState = null,
            setupDraftAttemptId = null,
            setupDraftRevision = null,
        )
        replaceState(updated)
        return OnboardingStateWrite.Written(updated)
    }

    @Transaction
    suspend fun selectProtocol(templateId: String): OnboardingStateWrite {
        val current = state() ?: initialOnboardingStateEntity()
        current.requireEligibilityGoalAndCurrentContexts()?.let {
            return OnboardingStateWrite.Rejected(it)
        }
        val updated = current.copy(
            progressKind = PROGRESS_IN_PROGRESS,
            progressStep = STEP_HEALTH_EXPLANATION,
            templateId = templateId,
            hasHealthState = false,
            healthCapabilityId = null,
            healthCapabilityValue = null,
            healthProviderAvailability = null,
            healthAccessOutcome = null,
            healthVisibleRecords = null,
            healthCoverage = null,
            healthFreshness = null,
            healthSuitability = null,
            manualPlanState = null,
            setupDraftAttemptId = null,
            setupDraftRevision = null,
        )
        replaceState(updated)
        return OnboardingStateWrite.Written(updated)
    }

    @Transaction
    suspend fun saveHealthState(health: PersistedHealthState): OnboardingStateWrite {
        val current = state() ?: initialOnboardingStateEntity()
        current.requireEligibilityGoalContextsAndTemplate()?.let {
            return OnboardingStateWrite.Rejected(it)
        }
        val updated = current.copy(
            progressKind = PROGRESS_IN_PROGRESS,
            progressStep = STEP_STATUS_COVERAGE,
            hasHealthState = true,
            healthCapabilityId = health.capabilityId,
            healthCapabilityValue = health.capabilityValue,
            healthProviderAvailability = health.providerAvailability,
            healthAccessOutcome = health.accessOutcome,
            healthVisibleRecords = health.visibleRecords,
            healthCoverage = health.coverage,
            healthFreshness = health.freshness,
            healthSuitability = health.suitability,
            manualPlanState = health.manualPlanState,
            setupDraftAttemptId = null,
            setupDraftRevision = null,
        )
        replaceState(updated)
        return OnboardingStateWrite.Written(updated)
    }

    @Transaction
    suspend fun saveSetupDraftReference(reference: PersistedSetupDraftReference): OnboardingStateWrite {
        val current = state() ?: initialOnboardingStateEntity()
        current.requireEligibilityGoalContextsTemplateAndHealth()?.let {
            return OnboardingStateWrite.Rejected(it)
        }
        val currentAttempt = current.setupDraftAttemptId
        val currentRevision = current.setupDraftRevision
        if (currentAttempt != null || currentRevision != null) {
            if (currentAttempt == null || currentRevision == null) {
                return OnboardingStateWrite.Rejected(OnboardingPrecondition.SETUP_DRAFT_MATCHES)
            }
            if (currentAttempt != reference.attemptId) {
                return OnboardingStateWrite.Rejected(OnboardingPrecondition.SETUP_DRAFT_MATCHES)
            }
            when {
                reference.revision < currentRevision -> {
                    return OnboardingStateWrite.Rejected(OnboardingPrecondition.MONOTONIC_SETUP_DRAFT_REVISION)
                }

                reference.revision == currentRevision -> {
                    if (current.isSetupCheckpoint()) {
                        return OnboardingStateWrite.Unchanged(current)
                    }
                    val active = activeProtocol()
                    if (current.isCompleted() && active != null &&
                        latestConfiguration(active.id)?.matches(reference) == true
                    ) {
                        return OnboardingStateWrite.Unchanged(current)
                    }
                }
            }
        }
        val updated = current.copy(
            progressKind = PROGRESS_IN_PROGRESS,
            progressStep = STEP_SETUP,
            setupDraftAttemptId = reference.attemptId,
            setupDraftRevision = reference.revision,
        )
        replaceState(updated)
        return OnboardingStateWrite.Written(updated)
    }

    @Transaction
    suspend fun createInitialActiveProtocol(
        protocolId: String,
        sourceDraft: PersistedSetupDraftReference,
    ): InitialProtocolInsert {
        val existingActive = activeProtocol()
        if (existingActive != null) {
            val current = state()
            val latest = latestConfiguration(existingActive.id)
            if (
                current != null && existingActive.status == ONBOARDING_ACTIVE_STATUS &&
                existingActive.templateId == current.templateId && current.isCompleted() &&
                current.matches(sourceDraft) && latest?.matches(sourceDraft) == true
            ) {
                return InitialProtocolInsert.Unchanged(existingActive, latest)
            }
            return InitialProtocolInsert.ActiveAlreadyExists
        }
        val current = state() ?: return InitialProtocolInsert.Rejected(OnboardingPrecondition.ELIGIBILITY_CONFIRMED)
        current.requireEligibilityGoalAndCurrentContexts()?.let { return InitialProtocolInsert.Rejected(it) }
        val templateId = current.templateId ?: return InitialProtocolInsert.MissingSelectedTemplate
        if (!current.hasHealthState) {
            return InitialProtocolInsert.Rejected(OnboardingPrecondition.HEALTH_STATE_RECORDED)
        }
        if (current.progressKind != PROGRESS_IN_PROGRESS || current.progressStep != STEP_SETUP) {
            return InitialProtocolInsert.Rejected(OnboardingPrecondition.SETUP_CHECKPOINT)
        }
        if (!current.matches(sourceDraft)) {
            return InitialProtocolInsert.Rejected(OnboardingPrecondition.SETUP_DRAFT_MATCHES)
        }
        when (ProtocolTemplateId.parsePersisted(templateId)) {
            is CatalogIdParseResult.Unknown -> return InitialProtocolInsert.InvalidSelectedTemplate
            is CatalogIdParseResult.Known -> Unit
        }
        val protocol = OnboardingProtocolEntity(
            id = protocolId,
            templateId = templateId,
            status = ONBOARDING_ACTIVE_STATUS,
            activeSlot = ONBOARDING_ACTIVE_SLOT,
        )
        val configuration = OnboardingProtocolConfigurationEntity(
            protocolId = protocolId,
            version = 1,
            sourceSetupDraftId = sourceDraft.attemptId,
            sourceSetupDraftRevision = sourceDraft.revision,
        )
        insertProtocol(protocol)
        insertConfiguration(configuration)
        replaceState(
            current.copy(
                progressKind = PROGRESS_COMPLETED,
                progressStep = null,
            ),
        )
        return InitialProtocolInsert.Created(protocol, configuration)
    }

    @Transaction
    suspend fun appendConfiguration(
        protocolId: String,
        sourceDraft: PersistedSetupDraftReference,
    ): AppendConfigurationInsert {
        val protocol = protocol(protocolId) ?: return AppendConfigurationInsert.MissingProtocol
        if (protocol.status != ONBOARDING_ACTIVE_STATUS || protocol.activeSlot != ONBOARDING_ACTIVE_SLOT ||
            activeProtocol()?.id != protocolId
        ) {
            return AppendConfigurationInsert.Rejected(OnboardingPrecondition.ACTIVE_PROTOCOL)
        }
        val current = state() ?: return AppendConfigurationInsert.Rejected(OnboardingPrecondition.SETUP_DRAFT_MATCHES)
        if (!current.matches(sourceDraft)) {
            return AppendConfigurationInsert.Rejected(OnboardingPrecondition.SETUP_DRAFT_MATCHES)
        }
        val latest = latestConfiguration(protocolId)
            ?: return AppendConfigurationInsert.Rejected(OnboardingPrecondition.LATEST_CONFIGURATION_MATCHES_DRAFT)
        if (latest.sourceSetupDraftId != sourceDraft.attemptId) {
            return AppendConfigurationInsert.Rejected(OnboardingPrecondition.LATEST_CONFIGURATION_MATCHES_DRAFT)
        }
        when {
            sourceDraft.revision < latest.sourceSetupDraftRevision -> {
                return AppendConfigurationInsert.Rejected(OnboardingPrecondition.MONOTONIC_SETUP_DRAFT_REVISION)
            }

            sourceDraft.revision == latest.sourceSetupDraftRevision -> {
                if (!current.isCompleted()) replaceState(current.completed())
                return AppendConfigurationInsert.Unchanged(protocol, latest)
            }
        }
        val configuration = OnboardingProtocolConfigurationEntity(
            protocolId = protocolId,
            version = latest.version + 1,
            sourceSetupDraftId = sourceDraft.attemptId,
            sourceSetupDraftRevision = sourceDraft.revision,
        )
        insertConfiguration(configuration)
        replaceState(current.completed())
        return AppendConfigurationInsert.Appended(protocol, configuration)
    }
}

internal data class PersistedHealthState(
    val capabilityId: String,
    val capabilityValue: String,
    val providerAvailability: String,
    val accessOutcome: String,
    val visibleRecords: String,
    val coverage: String,
    val freshness: String,
    val suitability: String,
    val manualPlanState: String,
)

internal data class PersistedSetupDraftReference(
    val attemptId: String,
    val revision: Long,
)

internal sealed interface OnboardingStateWrite {
    data class Written(val state: OnboardingStateEntity) : OnboardingStateWrite

    data class Unchanged(val state: OnboardingStateEntity) : OnboardingStateWrite

    data class Rejected(val precondition: OnboardingPrecondition) : OnboardingStateWrite
}

internal sealed interface InitialProtocolInsert {
    data class Created(
        val protocol: OnboardingProtocolEntity,
        val configuration: OnboardingProtocolConfigurationEntity,
    ) : InitialProtocolInsert

    data class Unchanged(
        val protocol: OnboardingProtocolEntity,
        val configuration: OnboardingProtocolConfigurationEntity,
    ) : InitialProtocolInsert

    data object ActiveAlreadyExists : InitialProtocolInsert

    data object MissingSelectedTemplate : InitialProtocolInsert

    data object InvalidSelectedTemplate : InitialProtocolInsert

    data class Rejected(val precondition: OnboardingPrecondition) : InitialProtocolInsert
}

internal sealed interface AppendConfigurationInsert {
    data class Appended(
        val protocol: OnboardingProtocolEntity,
        val configuration: OnboardingProtocolConfigurationEntity,
    ) : AppendConfigurationInsert

    data object MissingProtocol : AppendConfigurationInsert

    data class Unchanged(
        val protocol: OnboardingProtocolEntity,
        val configuration: OnboardingProtocolConfigurationEntity,
    ) : AppendConfigurationInsert

    data class Rejected(val precondition: OnboardingPrecondition) : AppendConfigurationInsert
}

private fun OnboardingStateEntity.isEligibilityConfirmed(): Boolean = eligibility == ELIGIBILITY_CONFIRMED

private fun OnboardingStateEntity.requireEligibilityAndGoal(): OnboardingPrecondition? = when {
    !isEligibilityConfirmed() -> OnboardingPrecondition.ELIGIBILITY_CONFIRMED
    goalId == null -> OnboardingPrecondition.GOAL_CONFIRMED
    else -> null
}

private fun OnboardingStateEntity.requireEligibilityGoalAndCurrentContexts(): OnboardingPrecondition? =
    requireEligibilityAndGoal() ?: when {
        !contextsConfirmed || contextsRequireConfirmation -> OnboardingPrecondition.CONTEXTS_CONFIRMED_AND_CURRENT
        else -> null
    }

private fun OnboardingStateEntity.requireEligibilityGoalContextsAndTemplate(): OnboardingPrecondition? =
    requireEligibilityGoalAndCurrentContexts() ?: when {
        templateId == null -> OnboardingPrecondition.TEMPLATE_SELECTED
        else -> null
    }

private fun OnboardingStateEntity.requireEligibilityGoalContextsTemplateAndHealth(): OnboardingPrecondition? =
    requireEligibilityGoalContextsAndTemplate() ?: when {
        !hasHealthState -> OnboardingPrecondition.HEALTH_STATE_RECORDED
        else -> null
    }

private fun OnboardingStateEntity.matches(reference: PersistedSetupDraftReference): Boolean =
    setupDraftAttemptId == reference.attemptId && setupDraftRevision == reference.revision

private fun OnboardingProtocolConfigurationEntity.matches(reference: PersistedSetupDraftReference): Boolean =
    sourceSetupDraftId == reference.attemptId && sourceSetupDraftRevision == reference.revision

private fun OnboardingStateEntity.isSetupCheckpoint(): Boolean =
    progressKind == PROGRESS_IN_PROGRESS && progressStep == STEP_SETUP

private fun OnboardingStateEntity.isCompleted(): Boolean =
    progressKind == PROGRESS_COMPLETED && progressStep == null

private fun OnboardingStateEntity.completed(): OnboardingStateEntity = copy(
    progressKind = PROGRESS_COMPLETED,
    progressStep = null,
)

internal fun initialOnboardingStateEntity(): OnboardingStateEntity = OnboardingStateEntity(
    singletonId = ONBOARDING_SINGLETON_ID,
    eligibility = ELIGIBILITY_UNCONFIRMED,
    progressKind = PROGRESS_NOT_STARTED,
    progressStep = null,
    goalId = null,
    contextsConfirmed = false,
    contextsRequireConfirmation = false,
    contextIds = "",
    templateId = null,
    hasHealthState = false,
    healthCapabilityId = null,
    healthCapabilityValue = null,
    healthProviderAvailability = null,
    healthAccessOutcome = null,
    healthVisibleRecords = null,
    healthCoverage = null,
    healthFreshness = null,
    healthSuitability = null,
    manualPlanState = null,
    setupDraftAttemptId = null,
    setupDraftRevision = null,
)

internal const val ELIGIBILITY_UNCONFIRMED = "UNCONFIRMED"
internal const val ELIGIBILITY_CONFIRMED = "CONFIRMED_ADULT"
internal const val PROGRESS_NOT_STARTED = "NOT_STARTED"
internal const val PROGRESS_IN_PROGRESS = "IN_PROGRESS"
internal const val PROGRESS_COMPLETED = "COMPLETED"
internal const val STEP_OUTCOME = "OUTCOME"
internal const val STEP_CONTEXT = "CONTEXT"
internal const val STEP_PROTOCOLS = "PROTOCOLS"
internal const val STEP_HEALTH_EXPLANATION = "HEALTH_EXPLANATION"
internal const val STEP_STATUS_COVERAGE = "STATUS_COVERAGE"
internal const val STEP_SETUP = "SETUP"
