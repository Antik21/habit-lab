package com.denis.habitlab.shared.presentation.experimenteditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denis.habitlab.shared.domain.interactor.CreateExperimentDraft
import com.denis.habitlab.shared.domain.interactor.EditExperimentDraft
import com.denis.habitlab.shared.domain.model.ExperimentId
import com.denis.habitlab.shared.domain.model.ExperimentName
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObservation
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObserver
import com.denis.habitlab.shared.domain.repository.CreateDraftResult
import com.denis.habitlab.shared.domain.repository.EditDraftResult
import com.denis.habitlab.shared.presentation.navigation.MetricPickerResult
import kotlinx.coroutines.flow.collect
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

class ExperimentEditorViewModel(
    private val experimentId: ExperimentId?,
    projectionObserver: ExperimentProjectionObserver,
    private val createExperimentDraft: CreateExperimentDraft,
    private val editExperimentDraft: EditExperimentDraft,
    private val uiMapper: ExperimentEditorUiMapper,
) : ViewModel(), ContainerHost<ViewState, SideEffect> {
    override val container: Container<ViewState, SideEffect> = viewModelScope.container(
        initialState = ViewState(
            experimentId = experimentId,
            content = if (experimentId == null) ContentUiModel.Ready else ContentUiModel.Loading,
        ),
        onCreate = {
            experimentId?.let { id ->
                projectionObserver.observe(id).collect { observation ->
                    reduce { uiMapper.map(observation, id, state) }
                    if (observation == ExperimentProjectionObservation.Missing) {
                        postSideEffect(NavigationEffect.PopToRoot)
                    }
                }
            }
        },
    )

    fun dispatchAction(action: Action) {
        when (action) {
            Action.BackClicked -> onBackClicked()
            is Action.NameChanged -> onNameChanged(action.value)
            Action.MetricClicked -> onMetricClicked()
            is Action.MetricResultDelivered -> onMetricResultDelivered(action.result)
            Action.SaveClicked -> onSaveClicked()
        }
    }

    private fun onBackClicked() = intent { postSideEffect(NavigationEffect.Back) }

    private fun onNameChanged(value: String) = intent {
        if (!state.isSaving && state.content == ContentUiModel.Ready) {
            reduce { state.copy(name = value, validationError = false, commandError = false) }
        }
    }

    private fun onMetricClicked() = intent {
        if (!state.isSaving && state.content == ContentUiModel.Ready) {
            postSideEffect(NavigationEffect.OpenMetricPicker(experimentId))
        }
    }

    private fun onMetricResultDelivered(result: MetricPickerResult) = intent {
        if (!state.isSaving && result.experimentId == experimentId && result is MetricPickerResult.Selected) {
            reduce { state.copy(metric = result.metric) }
        }
    }

    private fun onSaveClicked() = intent {
        if (state.isSaving || state.content != ContentUiModel.Ready) return@intent
        val name = ExperimentName.fromInput(state.name)
        if (name == null) {
            reduce { state.copy(validationError = true) }
            return@intent
        }
        reduce { state.copy(isSaving = true, validationError = false, commandError = false) }
        when (val result = save(experimentId, name)) {
            is SaveResult.Complete -> postSideEffect(NavigationEffect.SaveComplete(result.experimentId))
            SaveResult.Missing -> postSideEffect(NavigationEffect.PopToRoot)
            SaveResult.Error -> reduce { state.copy(isSaving = false, commandError = true) }
        }
    }

    private suspend fun save(experimentId: ExperimentId?, name: ExperimentName): SaveResult =
        if (experimentId == null) {
            when (val result = createExperimentDraft(name)) {
                is CreateDraftResult.Created -> SaveResult.Complete(result.draft.id)
                is CreateDraftResult.Failed -> SaveResult.Error
            }
        } else {
            when (editExperimentDraft(experimentId, name)) {
                is EditDraftResult.Updated -> SaveResult.Complete(experimentId)
                is EditDraftResult.Missing -> SaveResult.Missing
                is EditDraftResult.NotDraft, is EditDraftResult.Failed -> SaveResult.Error
            }
        }

    private sealed interface SaveResult {
        data class Complete(val experimentId: ExperimentId) : SaveResult
        data object Missing : SaveResult
        data object Error : SaveResult
    }
}
