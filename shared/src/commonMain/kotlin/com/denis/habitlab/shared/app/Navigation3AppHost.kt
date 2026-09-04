package com.denis.habitlab.shared.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.denis.habitlab.shared.presentation.gallery.ComponentGalleryScreen
import com.denis.habitlab.shared.presentation.navigation.NavigationDialogResultDisplay
import com.denis.habitlab.shared.presentation.navigation.NavigationExperimentDialogContent
import com.denis.habitlab.shared.presentation.navigation.NavigationExperimentScreen
import com.denis.habitlab.shared.presentation.navigation.NavigationFlowStepOneScreen
import com.denis.habitlab.shared.presentation.navigation.NavigationFlowStepTwoScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * A thin, common Navigation 3 shell. It owns the one user-managed back stack used by Android and
 * iOS; feature composables only receive callbacks and never retain routes or platform objects.
 */
@Composable
internal fun Navigation3AppHost(
    appTitle: String,
    navigationEvents: AppNavigationEventBridge,
) {
    val settingsCapability = rememberAppSettingsCapability()
    val backStack = rememberNavBackStack(navigationSavedStateConfiguration, AppDestination.Gallery)
    var lastDialogResult by remember { mutableStateOf<ExperimentDialogResult?>(null) }
    val navigator = remember(backStack) {
        AppNavigator(
            backStack = backStack,
            onDialogResult = { result -> lastDialogResult = result },
            onExperimentOpened = { lastDialogResult = null },
        )
    }
    val navigationEvent = navigationEvents.latestEvent
    val backRequestId = navigationEvents.latestBackRequestId

    LaunchedEffect(navigationEvent?.id) {
        navigationEvent?.let(navigator::handleExternalNavigation)
    }

    LaunchedEffect(backRequestId) {
        if (backRequestId > 0L) navigator.onBack()
    }

    val entries = remember(navigator, appTitle, lastDialogResult, settingsCapability) {
        entryProvider<NavKey> {
            entry<AppDestination.Gallery> {
                ComponentGalleryScreen(
                    appTitle = appTitle,
                    onBack = rememberDropUnlessResumedNavigationAction(navigator::onBack),
                    onOpenExperiment = rememberDropUnlessResumedNavigationAction(navigator::openExperiment),
                    onStartFlow = rememberDropUnlessResumedNavigationAction(navigator::startGalleryFlow),
                )
            }
            entry<AppDestination.Experiment> { route ->
                NavigationExperimentScreen(
                    experimentId = route.experimentId.value,
                    dialogResult = lastDialogResult.displayFor(route.experimentId),
                    onBack = rememberDropUnlessResumedNavigationAction(navigator::onBack),
                    onOpenDialog = rememberDropUnlessResumedNavigationAction {
                        navigator.openConfirmation(route.experimentId)
                    },
                    onStartFlow = rememberDropUnlessResumedNavigationAction {
                        navigator.startExperimentFlow(route.experimentId)
                    },
                    onOpenApplicationSettings = rememberDropUnlessResumedNavigationAction(
                        settingsCapability::openApplicationSettings,
                    ),
                )
            }
            entry<AppDestination.FlowStepOne> { route ->
                NavigationFlowStepOneScreen(
                    flowId = route.flowId.value,
                    onBack = rememberDropUnlessResumedNavigationAction(navigator::onBack),
                    onNext = rememberDropUnlessResumedNavigationAction {
                        navigator.advanceFlow(route.flowId)
                    },
                )
            }
            entry<AppDestination.FlowStepTwo> { route ->
                NavigationFlowStepTwoScreen(
                    flowId = route.flowId.value,
                    onBack = rememberDropUnlessResumedNavigationAction(navigator::onBack),
                    onFinish = rememberDropUnlessResumedNavigationAction {
                        navigator.completeFlow(route.flowId)
                    },
                )
            }
            entry<AppDestination.ConfirmExperiment>(
                metadata = { DialogSceneStrategy.dialog() },
            ) { route ->
                NavigationExperimentDialogContent(
                    experimentId = route.experimentId.value,
                    onConfirm = rememberDropUnlessResumedNavigationAction {
                        navigator.resolveConfirmation(route.experimentId, confirmed = true)
                    },
                    onCancel = rememberDropUnlessResumedNavigationAction {
                        navigator.resolveConfirmation(route.experimentId, confirmed = false)
                    },
                )
            }
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = navigator::onBack,
        sceneStrategies = listOf(DialogSceneStrategy(), SinglePaneSceneStrategy()),
        entryProvider = entries,
    )
}

/**
 * The only values that can enter the Nav3 back stack. They contain stable, serializable IDs only;
 * no screen models, UI state, or platform values are stored with a route.
 */
@Serializable
sealed interface AppDestination : NavKey {
    @Serializable
    data object Gallery : AppDestination

    @Serializable
    data class Experiment(val experimentId: ExperimentId) : AppDestination

    @Serializable
    data class FlowStepOne(val flowId: FlowId) : AppDestination

    @Serializable
    data class FlowStepTwo(val flowId: FlowId) : AppDestination

    @Serializable
    data class ConfirmExperiment(val experimentId: ExperimentId) : AppDestination
}

@Serializable
data class ExperimentId(
    val value: String,
) {
    companion object {
        private val knownValues = setOf("daily-movement", "sleep-routine")

        fun fromExternalValue(value: String): ExperimentId? =
            value.takeIf { it in knownValues }?.let(::ExperimentId)

    }
}

@Serializable
data class FlowId(val value: String)

/** Explicit result data delivered only after the dialog entry has been popped. */
sealed interface ExperimentDialogResult {
    val experimentId: ExperimentId

    data class Confirmed(override val experimentId: ExperimentId) : ExperimentDialogResult

    data class Cancelled(override val experimentId: ExperimentId) : ExperimentDialogResult
}

/** Common, host-safe signal for initial and repeated platform deep-link delivery. */
class AppNavigationEventBridge {
    private var nextEventId = 0L
    private var nextBackRequestId = 0L
    private var _latestEvent by mutableStateOf<ExternalNavigationEvent?>(null)
    private var _latestBackRequestId by mutableStateOf(0L)

    val latestEvent: ExternalNavigationEvent?
        get() = _latestEvent

    val latestBackRequestId: Long
        get() = _latestBackRequestId

    fun accept(rawUrl: String?) {
        nextEventId += 1
        _latestEvent = ExternalNavigationEvent(id = nextEventId, rawUrl = rawUrl)
    }

    fun requestBack() {
        nextBackRequestId += 1
        _latestBackRequestId = nextBackRequestId
    }
}

data class ExternalNavigationEvent(
    val id: Long,
    val rawUrl: String?,
)

/** Strict allowlist for external URLs. Every nonmatching form is deliberately a safe root route. */
object HabitLabDeepLink {
    private const val experimentPrefix = "habitlab://experiment/"

    fun parse(rawUrl: String?): AppDestination.Experiment? {
        val encodedId = rawUrl
            ?.takeIf { it.startsWith(experimentPrefix) }
            ?.removePrefix(experimentPrefix)
            ?.takeIf { it.isNotEmpty() && it.none { character -> character == '/' || character == '?' || character == '#' } }
            ?: return null

        return ExperimentId.fromExternalValue(encodedId)?.let(AppDestination::Experiment)
    }
}

private class AppNavigator(
    private val backStack: NavBackStack<NavKey>,
    private val onDialogResult: (ExperimentDialogResult) -> Unit,
    private val onExperimentOpened: () -> Unit,
) {
    fun openExperiment(externalId: String) {
        ExperimentId.fromExternalValue(externalId)?.let { experimentId ->
            onExperimentOpened()
            backStack += AppDestination.Experiment(experimentId)
        }
    }

    fun startGalleryFlow() {
        backStack += AppDestination.FlowStepOne(FlowId("gallery-setup"))
    }

    fun startExperimentFlow(experimentId: ExperimentId) {
        backStack += AppDestination.FlowStepOne(FlowId("experiment-${experimentId.value}"))
    }

    fun advanceFlow(flowId: FlowId) {
        backStack += AppDestination.FlowStepTwo(flowId)
    }

    fun completeFlow(flowId: FlowId) {
        if (backStack.lastOrNull() == AppDestination.FlowStepTwo(flowId)) {
            backStack.removeLast()
        }
        if (backStack.lastOrNull() == AppDestination.FlowStepOne(flowId)) {
            backStack.removeLast()
        }
    }

    fun openConfirmation(experimentId: ExperimentId) {
        backStack += AppDestination.ConfirmExperiment(experimentId)
    }

    fun resolveConfirmation(experimentId: ExperimentId, confirmed: Boolean) {
        if (backStack.lastOrNull() != AppDestination.ConfirmExperiment(experimentId)) return

        backStack.removeLast()
        onDialogResult(
            if (confirmed) {
                ExperimentDialogResult.Confirmed(experimentId)
            } else {
                ExperimentDialogResult.Cancelled(experimentId)
            },
        )
    }

    fun onBack() {
        val top = backStack.lastOrNull()
        if (top is AppDestination.ConfirmExperiment) {
            resolveConfirmation(top.experimentId, confirmed = false)
        } else if (backStack.size > 1) {
            backStack.removeLast()
        }
    }

    fun handleExternalNavigation(event: ExternalNavigationEvent) {
        onExperimentOpened()
        backStack.clear()
        backStack += AppDestination.Gallery
        HabitLabDeepLink.parse(event.rawUrl)?.let(backStack::add)
    }
}

private val navigationSavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(AppDestination.Gallery::class, AppDestination.Gallery.serializer())
            subclass(AppDestination.Experiment::class, AppDestination.Experiment.serializer())
            subclass(AppDestination.FlowStepOne::class, AppDestination.FlowStepOne.serializer())
            subclass(AppDestination.FlowStepTwo::class, AppDestination.FlowStepTwo.serializer())
            subclass(
                AppDestination.ConfirmExperiment::class,
                AppDestination.ConfirmExperiment.serializer(),
            )
        }
    }
}

private fun ExperimentDialogResult.toDisplay(): NavigationDialogResultDisplay = when (this) {
    is ExperimentDialogResult.Confirmed -> NavigationDialogResultDisplay.Confirmed
    is ExperimentDialogResult.Cancelled -> NavigationDialogResultDisplay.Cancelled
}

internal fun ExperimentDialogResult?.displayFor(
    experimentId: ExperimentId,
): NavigationDialogResultDisplay? = takeIf { it?.experimentId == experimentId }?.toDisplay()
