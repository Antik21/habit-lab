package com.denis.habitlab.shared.data.local

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Release catalog, seeded independently from the existing debug experiment fixture. */
@Entity(
    tableName = "onboarding_catalog_entries",
    primaryKeys = ["catalog_type", "catalog_id"],
    indices = [Index(value = ["catalog_type", "sort_position"], unique = true)],
)
internal data class OnboardingCatalogEntryEntity(
    @ColumnInfo(name = "catalog_type")
    val catalogType: String,
    @ColumnInfo(name = "catalog_id")
    val catalogId: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "sort_position")
    val sortPosition: Int,
    /** Only template entries may set this; all three v1 templates are manual-capable. */
    @ColumnInfo(name = "manual_capable")
    val manualCapable: Boolean,
)

/** Single-row state makes observer invalidation atomic even when a context selection is empty. */
@Entity(tableName = "onboarding_state")
internal data class OnboardingStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int,
    @ColumnInfo(name = "eligibility")
    val eligibility: String,
    @ColumnInfo(name = "progress_kind")
    val progressKind: String,
    @ColumnInfo(name = "progress_step")
    val progressStep: String?,
    @ColumnInfo(name = "goal_id")
    val goalId: String?,
    /** False means unanswered; true plus an empty string means explicitly confirmed empty. */
    @ColumnInfo(name = "contexts_confirmed")
    val contextsConfirmed: Boolean,
    @ColumnInfo(name = "contexts_require_confirmation")
    val contextsRequireConfirmation: Boolean,
    /** Canonical context-ID order, comma-delimited solely inside this aggregate row. */
    @ColumnInfo(name = "context_ids")
    val contextIds: String,
    @ColumnInfo(name = "template_id")
    val templateId: String?,
    @ColumnInfo(name = "has_health_state")
    val hasHealthState: Boolean,
    @ColumnInfo(name = "health_capability_id")
    val healthCapabilityId: String?,
    @ColumnInfo(name = "health_capability_value")
    val healthCapabilityValue: String?,
    @ColumnInfo(name = "health_provider_availability")
    val healthProviderAvailability: String?,
    @ColumnInfo(name = "health_access_outcome")
    val healthAccessOutcome: String?,
    @ColumnInfo(name = "health_visible_records")
    val healthVisibleRecords: String?,
    @ColumnInfo(name = "health_coverage")
    val healthCoverage: String?,
    @ColumnInfo(name = "health_freshness")
    val healthFreshness: String?,
    @ColumnInfo(name = "health_suitability")
    val healthSuitability: String?,
    @ColumnInfo(name = "manual_plan_state")
    val manualPlanState: String?,
    @ColumnInfo(name = "setup_draft_attempt_id")
    val setupDraftAttemptId: String?,
    @ColumnInfo(name = "setup_draft_revision")
    val setupDraftRevision: Long?,
)

@Entity(
    tableName = "onboarding_protocols",
    indices = [Index(value = ["active_slot"], unique = true)],
)
internal data class OnboardingProtocolEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "template_id")
    val templateId: String,
    @ColumnInfo(name = "status")
    val status: String,
    /** ACTIVE maps to 1. The unique index permits at most one onboarding protocol overall. */
    @ColumnInfo(name = "active_slot")
    val activeSlot: Int?,
)

@Entity(
    tableName = "onboarding_protocol_configurations",
    primaryKeys = ["protocol_id", "version"],
    foreignKeys = [
        ForeignKey(
            entity = OnboardingProtocolEntity::class,
            parentColumns = ["id"],
            childColumns = ["protocol_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["protocol_id"]),
        Index(value = ["source_setup_draft_id", "source_setup_draft_revision"], unique = true),
    ],
)
internal data class OnboardingProtocolConfigurationEntity(
    @ColumnInfo(name = "protocol_id")
    val protocolId: String,
    @ColumnInfo(name = "version")
    val version: Long,
    @ColumnInfo(name = "source_setup_draft_id")
    val sourceSetupDraftId: String,
    @ColumnInfo(name = "source_setup_draft_revision")
    val sourceSetupDraftRevision: Long,
)

/**
 * A single Room-query snapshot of the active protocol and its latest configuration.
 *
 * The nullable embedded configuration represents a genuine missing configuration after the left
 * join so the observer can expose invalid persistence instead of manufacturing a configuration.
 */
internal data class ActiveOnboardingProtocolSnapshot(
    @Embedded(prefix = "protocol_")
    val protocol: OnboardingProtocolEntity,
    @Embedded(prefix = "configuration_")
    val configuration: OnboardingProtocolConfigurationEntity?,
)

internal const val ONBOARDING_SINGLETON_ID = 1
internal const val ONBOARDING_ACTIVE_STATUS = "ACTIVE"
internal const val ONBOARDING_ACTIVE_SLOT = 1
