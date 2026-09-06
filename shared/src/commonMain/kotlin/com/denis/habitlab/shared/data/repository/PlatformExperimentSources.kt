package com.denis.habitlab.shared.data.repository

import com.denis.habitlab.shared.domain.interactor.RecordedAtSource
import com.denis.habitlab.shared.domain.interactor.ExperimentIdSource
import com.denis.habitlab.shared.domain.interactor.CurrentLocalDateSource
import com.denis.habitlab.shared.domain.model.RecordedAt
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.model.OnboardingProtocolId
import com.denis.habitlab.shared.domain.interactor.OnboardingProtocolIdSource
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.time.Clock

/** Infrastructure owns the system clock/randomness; command interactors receive these abstractions. */
internal class SystemRecordedAtSource(
    private val clock: Clock = Clock.System,
    private val timeZoneProvider: () -> TimeZone = TimeZone::currentSystemDefault,
) : RecordedAtSource {
    override fun now(): RecordedAt {
        val instant = clock.now()
        val timeZone = timeZoneProvider()
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

internal class RandomOnboardingProtocolIdSource(
    private val random: Random = Random.Default,
) : OnboardingProtocolIdSource {
    override fun nextId(): OnboardingProtocolId = requireNotNull(
        OnboardingProtocolId.fromPersisted(
            "onboarding-" + buildString {
                repeat(PROTOCOL_RANDOM_LENGTH) { append(PROTOCOL_ALPHABET[random.nextInt(PROTOCOL_ALPHABET.length)]) }
            },
        ),
    )

    private companion object {
        const val PROTOCOL_RANDOM_LENGTH = 20
        const val PROTOCOL_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"
    }
}

internal class RecordedAtCurrentLocalDateSource(
    private val recordedAtSource: RecordedAtSource,
) : CurrentLocalDateSource {
    override fun current() = recordedAtSource.now().localDate
}
