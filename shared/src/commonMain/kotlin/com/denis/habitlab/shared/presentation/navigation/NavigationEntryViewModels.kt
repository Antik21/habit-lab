package com.denis.habitlab.shared.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denis.habitlab.shared.domain.observer.ExperimentProjection
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObserver
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

/** Minimal Orbit state contract for the stateless gallery entry. */
data object GalleryUiState

sealed interface GalleryUiSideEffect {
    data class OpenExperiment(val experimentId: ExperimentId) : GalleryUiSideEffect

    data object StartFlow : GalleryUiSideEffect

    data object Back : GalleryUiSideEffect
}

class GalleryEntryViewModel : ViewModel(), ContainerHost<GalleryUiState, GalleryUiSideEffect> {
    override val container: Container<GalleryUiState, GalleryUiSideEffect> =
        viewModelScope.container(GalleryUiState)

    fun openExperiment(externalId: String) = intent {
        ExperimentId.fromExternalValue(externalId)?.let { experimentId ->
            postSideEffect(GalleryUiSideEffect.OpenExperiment(experimentId))
        } ?: postSideEffect(GalleryUiSideEffect.Back)
    }

    fun startFlow() = intent {
        postSideEffect(GalleryUiSideEffect.StartFlow)
    }

    fun back() = intent {
        postSideEffect(GalleryUiSideEffect.Back)
    }
}

/** Projection state remains in memory only and is rebuilt from [ExperimentProjectionObserver]. */
data class ExperimentUiState(
    val projection: ExperimentProjection? = null,
)

sealed interface ExperimentUiSideEffect {
    data object Back : ExperimentUiSideEffect

    data class OpenConfirmation(val experimentId: ExperimentId) : ExperimentUiSideEffect

    data class StartFlow(val experimentId: ExperimentId) : ExperimentUiSideEffect

    data object PopToRoot : ExperimentUiSideEffect

    /** One-shot typed result for the entry that opened the dialog; never placed in [ExperimentUiState]. */
    data class DialogResultDelivered(val result: ExperimentDialogResult) : ExperimentUiSideEffect
}

class ExperimentEntryViewModel(
    private val experimentId: ExperimentId,
    private val projectionObserver: ExperimentProjectionObserver,
) : ViewModel(), ContainerHost<ExperimentUiState, ExperimentUiSideEffect> {
    override val container: Container<ExperimentUiState, ExperimentUiSideEffect> =
        viewModelScope.container(ExperimentUiState())

    init {
        viewModelScope.launch {
            projectionObserver.observe(experimentId).collect { projection ->
                intent {
                    reduce { state.copy(projection = projection) }
                    if (projection == null) {
                        postSideEffect(ExperimentUiSideEffect.PopToRoot)
                    }
                }
            }
        }
    }

    fun back() = intent {
        postSideEffect(ExperimentUiSideEffect.Back)
    }

    fun openConfirmation() = intent {
        postSideEffect(ExperimentUiSideEffect.OpenConfirmation(experimentId))
    }

    fun startFlow() = intent {
        postSideEffect(ExperimentUiSideEffect.StartFlow(experimentId))
    }

    /** Called by the common host after it pops the dialog and verifies this is still its caller. */
    fun deliverDialogResult(result: ExperimentDialogResult) = intent {
        if (result.experimentId == experimentId) {
            postSideEffect(ExperimentUiSideEffect.DialogResultDelivered(result))
        }
    }
}

data class FlowUiState(
    val flowId: FlowId,
)

sealed interface FlowUiSideEffect {
    data object Back : FlowUiSideEffect

    data class Advance(val flowId: FlowId) : FlowUiSideEffect

    data class Complete(val flowId: FlowId) : FlowUiSideEffect

    data object PopToRoot : FlowUiSideEffect
}

class FlowEntryViewModel(
    private val flowId: FlowId,
) : ViewModel(), ContainerHost<FlowUiState, FlowUiSideEffect> {
    override val container: Container<FlowUiState, FlowUiSideEffect> =
        viewModelScope.container(FlowUiState(flowId))

    init {
        if (!FlowId.isSupported(flowId)) {
            intent { postSideEffect(FlowUiSideEffect.PopToRoot) }
        }
    }

    fun back() = intent {
        postSideEffect(FlowUiSideEffect.Back)
    }

    fun advance() = intent {
        postSideEffect(FlowUiSideEffect.Advance(flowId))
    }

    fun complete() = intent {
        postSideEffect(FlowUiSideEffect.Complete(flowId))
    }
}

data class ConfirmationDialogUiState(
    val experimentId: ExperimentId,
)

sealed interface ConfirmationDialogUiSideEffect {
    data class Resolve(val result: ExperimentDialogResult) : ConfirmationDialogUiSideEffect
}

class ConfirmationDialogEntryViewModel(
    private val experimentId: ExperimentId,
) : ViewModel(), ContainerHost<ConfirmationDialogUiState, ConfirmationDialogUiSideEffect> {
    override val container: Container<ConfirmationDialogUiState, ConfirmationDialogUiSideEffect> =
        viewModelScope.container(ConfirmationDialogUiState(experimentId))
    private var hasResolved = false

    fun confirm() = resolve(ExperimentDialogResult.Confirmed(experimentId))

    fun cancel() = resolve(ExperimentDialogResult.Cancelled(experimentId))

    private fun resolve(result: ExperimentDialogResult) = intent {
        if (!hasResolved) {
            hasResolved = true
            postSideEffect(ConfirmationDialogUiSideEffect.Resolve(result))
        }
    }
}

/** Typed, ephemeral return value. It is a side effect, not a serializable screen state field. */
sealed interface ExperimentDialogResult {
    val experimentId: ExperimentId

    data class Confirmed(override val experimentId: ExperimentId) : ExperimentDialogResult

    data class Cancelled(override val experimentId: ExperimentId) : ExperimentDialogResult
}
