package com.denis.habitlab.shared.domain.model

/**
 * A closed, persisted catalog identifier. Unknown stored values are deliberately represented by
 * [CatalogIdParseResult.Unknown]; callers must never silently substitute a catalog default.
 */
sealed interface CatalogIdParseResult<out T> {
    data class Known<T>(val value: T) : CatalogIdParseResult<T>

    data class Unknown<T>(val rawValue: String) : CatalogIdParseResult<T>
}

enum class GoalId(val persistedValue: String) {
    SLEEP_BETTER("sleep-better"),
    WAKE_REFRESHED("wake-refreshed"),
    MORNING_ENERGY("morning-energy"),
    CALM_EVENING("calm-evening"),
    DAILY_MOVEMENT("daily-movement"),
    ;

    companion object {
        fun parsePersisted(value: String): CatalogIdParseResult<GoalId> =
            entries.firstOrNull { it.persistedValue == value }
                ?.let { CatalogIdParseResult.Known<GoalId>(it) }
                ?: CatalogIdParseResult.Unknown<GoalId>(value)
    }
}

enum class ContextId(val persistedValue: String) {
    LOW_EVENING_MOVEMENT("low-evening-movement"),
    SCREEN_BEFORE_SLEEP("screen-before-sleep"),
    IRREGULAR_SLEEP_TIME("irregular-sleep-time"),
    LATE_MEAL("late-meal"),
    HARD_TO_UNWIND("hard-to-unwind"),
    VARIABLE_SCHEDULE("variable-schedule"),
    NOT_SURE_YET("not-sure-yet"),
    ;

    companion object {
        fun parsePersisted(value: String): CatalogIdParseResult<ContextId> =
            entries.firstOrNull { it.persistedValue == value }
                ?.let { CatalogIdParseResult.Known<ContextId>(it) }
                ?: CatalogIdParseResult.Unknown<ContextId>(value)
    }
}

enum class ProtocolTemplateId(val persistedValue: String) {
    AFTER_DINNER_WALK("after-dinner-walk"),
    CALM_EVENING_RITUAL("calm-evening-ritual"),
    REGULAR_SLEEP_SCHEDULE("regular-sleep-schedule"),
    ;

    companion object {
        fun parsePersisted(value: String): CatalogIdParseResult<ProtocolTemplateId> =
            entries.firstOrNull { it.persistedValue == value }
                ?.let { CatalogIdParseResult.Known<ProtocolTemplateId>(it) }
                ?: CatalogIdParseResult.Unknown<ProtocolTemplateId>(value)
    }
}

enum class MetricId(val persistedValue: String) {
    SLEEP_DURATION("sleep-duration"),
    SLEEP_SESSION_DURATION("sleep-session-duration"),
    MORNING_ENERGY("morning-energy"),
    SUBJECTIVE_SLEEP_QUALITY("subjective-sleep-quality"),
    SLEEP_TIMING_VARIABILITY("sleep-timing-variability"),
    SLEEP_ATTEMPT_TIME("sleep-attempt-time"),
    ;

    companion object {
        fun parsePersisted(value: String): CatalogIdParseResult<MetricId> =
            entries.firstOrNull { it.persistedValue == value }
                ?.let { CatalogIdParseResult.Known<MetricId>(it) }
                ?: CatalogIdParseResult.Unknown<MetricId>(value)
    }
}

/** Catalog metadata intentionally says nothing about metrics, platform permissions, or ranking. */
data class ProtocolTemplate(
    val id: ProtocolTemplateId,
    val displayName: String,
    val manualCapable: Boolean,
)

data class OnboardingCatalog(
    val goals: List<GoalId>,
    val contexts: List<ContextId>,
    val templates: List<ProtocolTemplate>,
    val metrics: List<MetricId>,
)
