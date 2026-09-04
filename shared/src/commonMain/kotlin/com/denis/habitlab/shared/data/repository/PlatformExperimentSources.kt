package com.denis.habitlab.shared.data.repository

import com.denis.habitlab.shared.domain.interactor.RecordedAtSource
import com.denis.habitlab.shared.domain.interactor.ExperimentIdSource
import com.denis.habitlab.shared.domain.model.RecordedAt
import com.denis.habitlab.shared.domain.model.ExperimentId
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.time.Clock

/** Infrastructure owns the system clock/randomness; command interactors receive these abstractions. */
internal class SystemRecordedAtSource(
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : RecordedAtSource {
    override fun now(): RecordedAt {
        val instant = clock.now()
        return RecordedAt(
            utcInstant = instant,
            originalOffset = timeZone.offsetAt(instant),
            localDate = instant.toLocalDateTime(timeZone).date,
        )
    }
}

internal class RandomDraftExperimentIdSource(
    private val random: Random = Random.Default,
) : ExperimentIdSource {
    override fun nextDraftId(): ExperimentId = ExperimentId(
        value = "draft-" + buildString {
            repeat(DRAFT_RANDOM_LENGTH) { append(DRAFT_ALPHABET[random.nextInt(DRAFT_ALPHABET.length)]) }
        },
    )

    private companion object {
        const val DRAFT_RANDOM_LENGTH = 20
        const val DRAFT_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"
    }
}
