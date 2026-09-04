package com.denis.habitlab.shared.domain.interactor

import com.denis.habitlab.shared.domain.model.CheckInOutcome
import com.denis.habitlab.shared.domain.model.DailyCheckIn
import com.denis.habitlab.shared.domain.model.Experiment
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.model.ExperimentName
import com.denis.habitlab.shared.domain.model.ExperimentStatus
import com.denis.habitlab.shared.domain.model.RecordedAt
import kotlinx.datetime.LocalDate
import com.denis.habitlab.shared.domain.repository.CreateDraftResult
import com.denis.habitlab.shared.domain.repository.EditDraftResult
import com.denis.habitlab.shared.domain.repository.ExperimentRepository
import com.denis.habitlab.shared.domain.repository.RecordDailyCheckInResult

interface ExperimentIdSource {
    fun nextDraftId(): ExperimentId
}

/** Supplies submission timestamps; factual habit time is explicitly provided as [OccurredAt]. */
interface RecordedAtSource {
    fun now(): RecordedAt
}

class CreateExperimentDraft(
    private val repository: ExperimentRepository,
    private val idSource: ExperimentIdSource,
    private val recordedAtSource: RecordedAtSource,
) {
    suspend operator fun invoke(name: ExperimentName): CreateDraftResult {
        val recordedAt = recordedAtSource.now()
        val draft = Experiment(
            id = idSource.nextDraftId(),
            name = name,
            status = ExperimentStatus.DRAFT,
            createdAt = recordedAt,
            updatedAt = recordedAt,
        )
        return repository.createDraft(draft)
    }
}

class EditExperimentDraft(
    private val repository: ExperimentRepository,
    private val recordedAtSource: RecordedAtSource,
) {
    suspend operator fun invoke(
        experimentId: ExperimentId,
        name: ExperimentName,
    ): EditDraftResult = repository.editDraft(experimentId, name, recordedAtSource.now())
}

class RecordDailyCheckIn(
    private val repository: ExperimentRepository,
    private val recordedAtSource: RecordedAtSource,
) {
    suspend operator fun invoke(
        experimentId: ExperimentId,
        localDate: LocalDate,
        outcome: CheckInOutcome,
    ): RecordDailyCheckInResult = repository.recordDailyCheckIn(
        DailyCheckIn(
            experimentId = experimentId,
            localDate = localDate,
            outcome = outcome,
            recordedAt = recordedAtSource.now(),
        ),
    )
}
