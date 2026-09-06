package com.denis.habitlab.shared.data.mapper

import com.denis.habitlab.shared.data.local.OnboardingCatalogEntryEntity
import com.denis.habitlab.shared.data.local.OnboardingProtocolConfigurationEntity
import com.denis.habitlab.shared.data.local.OnboardingProtocolEntity
import com.denis.habitlab.shared.data.local.OnboardingStateEntity
import com.denis.habitlab.shared.data.local.PersistedHealthState
import com.denis.habitlab.shared.data.local.PersistedSetupDraftReference
import com.denis.habitlab.shared.domain.model.ActiveOnboardingProtocol
import com.denis.habitlab.shared.domain.model.CatalogIdParseResult
import com.denis.habitlab.shared.domain.model.ConfirmedContextSelection
import com.denis.habitlab.shared.domain.model.ContextId
import com.denis.habitlab.shared.domain.model.EligibilityConfirmation
import com.denis.habitlab.shared.domain.model.GoalId
import com.denis.habitlab.shared.domain.model.HealthAccessOutcome
import com.denis.habitlab.shared.domain.model.HealthCapabilityId
import com.denis.habitlab.shared.domain.model.HealthCapabilityValue
import com.denis.habitlab.shared.domain.model.HealthCoverageState
import com.denis.habitlab.shared.domain.model.HealthFreshnessState
import com.denis.habitlab.shared.domain.model.HealthProviderAvailability
import com.denis.habitlab.shared.domain.model.HealthSuitabilityState
import com.denis.habitlab.shared.domain.model.ManualPlanState
import com.denis.habitlab.shared.domain.model.MetricId
import com.denis.habitlab.shared.domain.model.OnboardingCatalog
import com.denis.habitlab.shared.domain.model.OnboardingHealthState
import com.denis.habitlab.shared.domain.model.OnboardingProgress
import com.denis.habitlab.shared.domain.model.OnboardingProtocolId
import com.denis.habitlab.shared.domain.model.OnboardingSelections
import com.denis.habitlab.shared.domain.model.OnboardingState
import com.denis.habitlab.shared.domain.model.OnboardingStep
import com.denis.habitlab.shared.domain.model.ProtocolTemplate
import com.denis.habitlab.shared.domain.model.ProtocolTemplateId
import com.denis.habitlab.shared.domain.model.SetupDraftReference
import com.denis.habitlab.shared.domain.model.StoredContextSelection
import com.denis.habitlab.shared.domain.model.VersionedProtocolConfiguration
import com.denis.habitlab.shared.domain.model.VisibleHealthRecordOutcome
import com.denis.habitlab.shared.domain.observer.InvalidOnboardingPersistence

/** Data mappers never choose a replacement for corrupt persisted catalog data. */
internal sealed interface OnboardingDecode<out T> {
    data class Valid<T>(val value: T) : OnboardingDecode<T>

    data class Invalid(val reason: InvalidOnboardingPersistence) : OnboardingDecode<Nothing>
}

internal fun OnboardingStateEntity.toDomainOnboardingState(): OnboardingDecode<OnboardingState> {
    val eligibility = enumValue<EligibilityConfirmation>("eligibility", eligibility)
        ?: return invalid("eligibility", eligibility)
    val progress = progress() ?: return invalid("progress", "$progressKind/$progressStep")
    val goal = goalId?.let { GoalId.parsePersisted(it).orInvalid("goal_id") }
        ?: if (goalId == null) null else return invalid("goal_id", goalId)
    val contexts = when (val decoded = contexts()) {
        is OnboardingDecode.Valid -> decoded.value
        is OnboardingDecode.Invalid -> return decoded
    }
    val template = templateId?.let { ProtocolTemplateId.parsePersisted(it).orInvalid("template_id") }
        ?: if (templateId == null) null else return invalid("template_id", templateId)
    val health = when (val decoded = health()) {
        is OnboardingDecode.Valid -> decoded.value
        is OnboardingDecode.Invalid -> return decoded
    }
    val draft = when (val decoded = setupDraft()) {
        is OnboardingDecode.Valid -> decoded.value
        is OnboardingDecode.Invalid -> return decoded
    }
    return OnboardingDecode.Valid(
        OnboardingState(
            eligibility = eligibility,
            progress = progress,
            selections = OnboardingSelections(
                goal = goal,
                contexts = contexts,
                template = template,
                health = health,
                setupDraft = draft,
            ),
        ),
    )
}

internal fun OnboardingHealthState.toPersisted(): PersistedHealthState = PersistedHealthState(
    capabilityId = capability.persistedValue,
    capabilityValue = capabilityValue.name,
    providerAvailability = providerAvailability.name,
    accessOutcome = accessOutcome.name,
    visibleRecords = visibleRecords.name,
    coverage = coverage.name,
    freshness = freshness.name,
    suitability = suitability.name,
    manualPlanState = manualPlan.name,
)

internal fun SetupDraftReference.toPersisted(): PersistedSetupDraftReference = PersistedSetupDraftReference(
    attemptId = attemptId.value,
    revision = revision,
)

internal fun List<OnboardingCatalogEntryEntity>.toDomainOnboardingCatalog(): OnboardingDecode<OnboardingCatalog> {
    val byType = groupBy { it.catalogType }
    if (byType.keys != setOf(CATALOG_GOAL, CATALOG_CONTEXT, CATALOG_TEMPLATE, CATALOG_METRIC)) {
        return invalid("catalog_type", byType.keys.sorted().joinToString(","))
    }
    val goals = byType.requireEntries(CATALOG_GOAL).decodeIds(GoalId.entries) { GoalId.parsePersisted(it) }
        ?: return invalid("catalog.goal", "unknown, duplicate, or incomplete")
    val contexts = byType.requireEntries(CATALOG_CONTEXT).decodeIds(ContextId.entries) { ContextId.parsePersisted(it) }
        ?: return invalid("catalog.context", "unknown, duplicate, or incomplete")
    val metrics = byType.requireEntries(CATALOG_METRIC).decodeIds(MetricId.entries) { MetricId.parsePersisted(it) }
        ?: return invalid("catalog.metric", "unknown, duplicate, or incomplete")
    val templateEntries = byType.requireEntries(CATALOG_TEMPLATE)
    if (templateEntries.size != ProtocolTemplateId.entries.size || templateEntries.any { !it.manualCapable }) {
        return invalid("catalog.template", "v1 requires exactly three manual-capable templates")
    }
    val templates = templateEntries.sortedBy { it.sortPosition }.map { entry ->
        when (val parsed = ProtocolTemplateId.parsePersisted(entry.catalogId)) {
            is CatalogIdParseResult.Known -> ProtocolTemplate(parsed.value, entry.displayName, entry.manualCapable)
            is CatalogIdParseResult.Unknown -> return invalid("catalog.template_id", parsed.rawValue)
        }
    }
    if (templates.map(ProtocolTemplate::id) != ProtocolTemplateId.entries) {
        return invalid("catalog.template", "unknown, duplicate, or incomplete")
    }
    return OnboardingDecode.Valid(OnboardingCatalog(goals, contexts, templates, metrics))
}

internal fun OnboardingProtocolEntity.toDomain(
    configuration: OnboardingProtocolConfigurationEntity,
): OnboardingDecode<ActiveOnboardingProtocol> {
    if (status != "ACTIVE" || activeSlot != 1) return invalid("onboarding_protocol.status", "$status/$activeSlot")
    val protocolId = OnboardingProtocolId.fromPersisted(id) ?: return invalid("onboarding_protocol.id", id)
    val template = ProtocolTemplateId.parsePersisted(templateId).orInvalid("onboarding_protocol.template_id")
        ?: return invalid("onboarding_protocol.template_id", templateId)
    if (configuration.protocolId != id || configuration.version < 1) {
        return invalid("onboarding_protocol.configuration", "mismatched protocol or version")
    }
    val attempt = com.denis.habitlab.shared.domain.model.OnboardingAttemptId
        .fromPersisted(configuration.sourceSetupDraftId)
        ?: return invalid("onboarding_protocol.configuration.source_setup_draft_id", configuration.sourceSetupDraftId)
    if (configuration.sourceSetupDraftRevision < 1) {
        return invalid("onboarding_protocol.configuration.source_setup_draft_revision", configuration.sourceSetupDraftRevision.toString())
    }
    return OnboardingDecode.Valid(
        ActiveOnboardingProtocol(
            id = protocolId,
            template = template,
            configuration = VersionedProtocolConfiguration(
                version = configuration.version,
                sourceSetupDraft = SetupDraftReference(attempt, configuration.sourceSetupDraftRevision),
            ),
        ),
    )
}

private fun OnboardingStateEntity.progress(): OnboardingProgress? = when (progressKind) {
    "NOT_STARTED" -> if (progressStep == null) OnboardingProgress.NotStarted else null
    "COMPLETED" -> if (progressStep == null) OnboardingProgress.Completed else null
    "IN_PROGRESS" -> progressStep?.let { step ->
        enumValue<OnboardingStep>("progress_step", step)?.let(OnboardingProgress::InProgress)
    }

    else -> null
}

private fun OnboardingStateEntity.contexts(): OnboardingDecode<StoredContextSelection?> {
    if (!contextsConfirmed) {
        return if (contextIds.isEmpty() && !contextsRequireConfirmation) {
            OnboardingDecode.Valid(null)
        } else {
            invalid("context_ids", "unconfirmed selection contains persisted answer")
        }
    }
    val values = if (contextIds.isEmpty()) {
        emptySet()
    } else {
        contextIds.split(',').map { raw ->
            when (val parsed = ContextId.parsePersisted(raw)) {
                is CatalogIdParseResult.Known -> parsed.value
                is CatalogIdParseResult.Unknown -> return invalid("context_ids", parsed.rawValue)
            }
        }.toSet()
    }
    if (!contextIds.isEmpty() && values.size != contextIds.split(',').size) {
        return invalid("context_ids", "duplicate IDs")
    }
    val selection = if (values.isEmpty()) {
        ConfirmedContextSelection.ExplicitlyEmpty
    } else {
        when (val validation = ConfirmedContextSelection.Selected.from(values)) {
            is com.denis.habitlab.shared.domain.model.ContextSelectionValidation.Valid -> validation.selection
            else -> return invalid("context_ids", "not-sure-yet mixed with other contexts")
        }
    }
    return OnboardingDecode.Valid(StoredContextSelection(selection, contextsRequireConfirmation))
}

private fun OnboardingStateEntity.health(): OnboardingDecode<OnboardingHealthState?> {
    if (!hasHealthState) {
        return if (
            healthCapabilityId == null && healthCapabilityValue == null && healthProviderAvailability == null &&
            healthAccessOutcome == null && healthVisibleRecords == null && healthCoverage == null &&
            healthFreshness == null && healthSuitability == null && manualPlanState == null
        ) {
            OnboardingDecode.Valid(null)
        } else {
            invalid("health_state", "absent state has persisted axis")
        }
    }
    val capability = healthCapabilityId?.let { HealthCapabilityId.parsePersisted(it).orInvalid("health_capability_id") }
        ?: return invalid("health_capability_id", healthCapabilityId ?: "null")
    val capabilityValue = healthCapabilityValue?.let { enumValue<HealthCapabilityValue>("health_capability_value", it) }
        ?: return invalid("health_capability_value", healthCapabilityValue ?: "null")
    val availability = healthProviderAvailability?.let { enumValue<HealthProviderAvailability>("health_provider_availability", it) }
        ?: return invalid("health_provider_availability", healthProviderAvailability ?: "null")
    val access = healthAccessOutcome?.let { enumValue<HealthAccessOutcome>("health_access_outcome", it) }
        ?: return invalid("health_access_outcome", healthAccessOutcome ?: "null")
    val visible = healthVisibleRecords?.let { enumValue<VisibleHealthRecordOutcome>("health_visible_records", it) }
        ?: return invalid("health_visible_records", healthVisibleRecords ?: "null")
    val coverage = healthCoverage?.let { enumValue<HealthCoverageState>("health_coverage", it) }
        ?: return invalid("health_coverage", healthCoverage ?: "null")
    val freshness = healthFreshness?.let { enumValue<HealthFreshnessState>("health_freshness", it) }
        ?: return invalid("health_freshness", healthFreshness ?: "null")
    val suitability = healthSuitability?.let { enumValue<HealthSuitabilityState>("health_suitability", it) }
        ?: return invalid("health_suitability", healthSuitability ?: "null")
    val manual = manualPlanState?.let { enumValue<ManualPlanState>("manual_plan_state", it) }
        ?: return invalid("manual_plan_state", manualPlanState ?: "null")
    return OnboardingDecode.Valid(
        OnboardingHealthState(capability, capabilityValue, availability, access, visible, coverage, freshness, suitability, manual),
    )
}

private fun OnboardingStateEntity.setupDraft(): OnboardingDecode<SetupDraftReference?> = when {
    setupDraftAttemptId == null && setupDraftRevision == null -> OnboardingDecode.Valid(null)
    setupDraftAttemptId == null || setupDraftRevision == null -> invalid("setup_draft", "partial reference")
    else -> com.denis.habitlab.shared.domain.model.OnboardingAttemptId.fromPersisted(setupDraftAttemptId)
        ?.takeIf { setupDraftRevision >= 1 }
        ?.let { OnboardingDecode.Valid(SetupDraftReference(it, setupDraftRevision)) }
        ?: invalid("setup_draft", "$setupDraftAttemptId/$setupDraftRevision")
}

private fun Map<String, List<OnboardingCatalogEntryEntity>>.requireEntries(type: String): List<OnboardingCatalogEntryEntity> =
    getValue(type)

private fun <T> List<OnboardingCatalogEntryEntity>.decodeIds(
    expected: List<T>,
    parse: (String) -> CatalogIdParseResult<T>,
): List<T>? {
    if (size != expected.size || any { it.manualCapable }) return null
    val decoded = sortedBy { it.sortPosition }.map { entry ->
        when (val parsed = parse(entry.catalogId)) {
            is CatalogIdParseResult.Known -> parsed.value
            is CatalogIdParseResult.Unknown -> return null
        }
    }
    return decoded.takeIf { it == expected }
}

private fun <T> CatalogIdParseResult<T>.orInvalid(field: String): T? = when (this) {
    is CatalogIdParseResult.Known -> value
    is CatalogIdParseResult.Unknown -> null
}

private inline fun <reified T : Enum<T>> enumValue(field: String, raw: String): T? =
    enumValues<T>().firstOrNull { it.name == raw }

private fun invalid(field: String, raw: String): OnboardingDecode.Invalid =
    OnboardingDecode.Invalid(InvalidOnboardingPersistence(field, raw))

private const val CATALOG_GOAL = "GOAL"
private const val CATALOG_CONTEXT = "CONTEXT"
private const val CATALOG_TEMPLATE = "TEMPLATE"
private const val CATALOG_METRIC = "METRIC"
