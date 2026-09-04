package com.denis.habitlab.shared.data.local

import kotlin.time.Instant

/** Fixed persisted fixture used only by the explicitly enabled debug runtime control. */
internal data class DebugSeed(
    val experiments: List<ExperimentEntity>,
    val checkIns: List<CheckInEntity>,
) {
    companion object {
        val fixed: DebugSeed by lazy {
            DebugSeed(
                experiments = listOf(
                    ExperimentEntity(
                        id = "daily-movement",
                        displayName = "Daily movement",
                        status = "ACTIVE",
                        activeSlot = ACTIVE_SLOT,
                        createdUtcMillis = millis("2026-01-01T08:00:00Z"),
                        createdOffsetSeconds = 0,
                        createdLocalDate = "2026-01-01",
                        updatedUtcMillis = millis("2026-01-01T08:00:00Z"),
                        updatedOffsetSeconds = 0,
                        updatedLocalDate = "2026-01-01",
                    ),
                    ExperimentEntity(
                        id = "sleep-routine",
                        displayName = "Sleep routine",
                        status = "DRAFT",
                        activeSlot = null,
                        createdUtcMillis = millis("2026-01-01T08:05:00Z"),
                        createdOffsetSeconds = 0,
                        createdLocalDate = "2026-01-01",
                        updatedUtcMillis = millis("2026-01-01T08:05:00Z"),
                        updatedOffsetSeconds = 0,
                        updatedLocalDate = "2026-01-01",
                    ),
                ),
                checkIns = listOf(
                    CheckInEntity(
                        experimentId = "daily-movement",
                        checkInLocalDate = "2026-01-02",
                        outcome = "PERFORMED",
                        occurredUtcMillis = millis("2026-01-02T18:00:00Z"),
                        occurredOffsetSeconds = 0,
                        recordedUtcMillis = millis("2026-01-02T18:01:00Z"),
                        recordedOffsetSeconds = 0,
                        recordedLocalDate = "2026-01-02",
                    ),
                    CheckInEntity(
                        experimentId = "sleep-routine",
                        checkInLocalDate = "2026-01-02",
                        outcome = "SKIPPED",
                        occurredUtcMillis = null,
                        occurredOffsetSeconds = null,
                        recordedUtcMillis = millis("2026-01-02T21:01:00Z"),
                        recordedOffsetSeconds = 0,
                        recordedLocalDate = "2026-01-02",
                    ),
                ),
            )
        }

        private fun millis(instant: String): Long = Instant.parse(instant).toEpochMilliseconds()

        private const val ACTIVE_SLOT = 1
    }
}
