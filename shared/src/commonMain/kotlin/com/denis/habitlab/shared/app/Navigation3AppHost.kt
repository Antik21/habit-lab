package com.denis.habitlab.shared.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.denis.habitlab.shared.presentation.gallery.ComponentGalleryScreen
import com.denis.habitlab.shared.presentation.gallery.ComponentGalleryViewModel
import com.denis.habitlab.shared.presentation.gallery.NavigationEffect as GalleryNavigationEffect
import com.denis.habitlab.shared.presentation.navigation.ExperimentDialogResult as NavigationDialogResult
import com.denis.habitlab.shared.presentation.navigation.confirmation.NavigationConfirmationDialogScreen
import com.denis.habitlab.shared.presentation.navigation.confirmation.NavigationConfirmationDialogViewModel
import com.denis.habitlab.shared.presentation.navigation.confirmation.NavigationEffect as ConfirmationNavigationEffect
import com.denis.habitlab.shared.presentation.navigation.experiment.NavigationEffect as ExperimentNavigationEffect
import com.denis.habitlab.shared.presentation.navigation.experiment.NavigationDialogResultDisplay
import com.denis.habitlab.shared.presentation.navigation.experiment.NavigationExperimentScreen
import com.denis.habitlab.shared.presentation.navigation.experiment.NavigationExperimentViewModel
import com.denis.habitlab.shared.presentation.navigation.flow.stepone.NavigationEffect as FlowStepOneNavigationEffect
import com.denis.habitlab.shared.presentation.navigation.flow.stepone.NavigationFlowStepOneScreen
import com.denis.habitlab.shared.presentation.navigation.flow.stepone.NavigationFlowStepOneViewModel
import com.denis.habitlab.shared.presentation.navigation.flow.steptwo.NavigationEffect as FlowStepTwoNavigationEffect
import com.denis.habitlab.shared.presentation.navigation.flow.steptwo.NavigationFlowStepTwoScreen
import com.denis.habitlab.shared.presentation.navigation.flow.steptwo.NavigationFlowStepTwoViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Common, app-owned Navigation 3 shell. It is the only component that mutates the shared stack;
 * entry ViewModels express navigation exclusively as Orbit one-shot side effects.
 */
@Composable
internal fun Navigation3AppHost(
    appTitle: String,
    navigationEvents: AppNavigationEventBridge,
) {
    val settingsCapability = rememberAppSettingsCapability()
    val snapshotStore = rememberNavigationRouteSnapshotStore()
    val restoredSnapshot = remember(snapshotStore) {
        NavigationRouteSnapshotCodec.restore(snapshotStore.read())
    }
    val backStack = rememberNavBackStack(
        navigationSavedStateConfiguration,
        *restoredSnapshot.routes.toTypedArray(),
    )
    var pendingDialogDelivery by remember { mutableStateOf<DialogResultDelivery?>(null) }
    var visibleDialogResult by remember { mutableStateOf<NavigationDialogResult?>(null) }
    val navigator = remember(backStack, snapshotStore) {
        AppNavigator(
            backStack = backStack,
            snapshotStore = snapshotStore,
            onDialogResult = { delivery -> pendingDialogDelivery = delivery },
            onNavigationStarted = {
                pendingDialogDelivery = null
                visibleDialogResult = null
            },
        )
    }
    val navigationEvent = navigationEvents.latestEvent

    LaunchedEffect(restoredSnapshot.shouldClearStoredSnapshot, snapshotStore) {
        if (restoredSnapshot.shouldClearStoredSnapshot) {
            snapshotStore.clear()
        }
    }
    LaunchedEffect(navigationEvent?.id) {
        navigationEvent?.let { event ->
            navigator.handleExternalNavigation(event)
            navigationEvents.consume(event.id)
        }
    }
    LaunchedEffect(navigationEvents, navigator) {
        navigationEvents.backRequests.collect {
            withContext(NonCancellable) { navigator.onBack() }
        }
    }
    val entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
        rememberViewModelStoreNavEntryDecorator<NavKey>(),
    )
    val entries = remember(
        appTitle,
        navigator,
        settingsCapability,
        pendingDialogDelivery,
        visibleDialogResult,
    ) {
        entryProvider<NavKey> {
            entry<AppDestination.Gallery> {
                val viewModel: ComponentGalleryViewModel = navigationEntryViewModel(key = "gallery")

                ComponentGalleryScreen(
                    appTitle = appTitle,
                    viewModel = viewModel,
                    isNavigationActionAllowed = rememberIsNavigationActionAllowed(),
                    handleNavigationAction = navigator::handleGalleryEffect,
                )
            }
            entry<AppDestination.Experiment> { route ->
                val viewModel: NavigationExperimentViewModel = navigationEntryViewModel(
                    key = "experiment:${route.experimentId.value}",
                    route.experimentId,
                )
                val delivery = pendingDialogDelivery?.takeIf { candidate ->
                    candidate.experimentId == route.experimentId && backStack.lastOrNull() == route
                }

                NavigationExperimentScreen(
                    viewModel = viewModel,
                    deliveredDialogResult = delivery?.result,
                    dialogResult = visibleDialogResult.displayFor(route.experimentId),
                    isNavigationActionAllowed = rememberIsNavigationActionAllowed(),
                    onDialogResultConsumed = {
                        if (pendingDialogDelivery?.id == delivery?.id) {
                            pendingDialogDelivery = null
                        }
                    },
                    openApplicationSettings = settingsCapability::openApplicationSettings,
                    handleNavigationAction = { effect ->
                        navigator.handleExperimentEffect(route, effect)?.let { result ->
                            visibleDialogResult = result
                        }
                    },
                )
            }
            entry<AppDestination.FlowStepOne> { route ->
                val viewModel: NavigationFlowStepOneViewModel = navigationEntryViewModel(
                    key = "flow-step-one:${route.flowId.value}",
                    route.flowId,
                )

                NavigationFlowStepOneScreen(
                    viewModel = viewModel,
                    isNavigationActionAllowed = rememberIsNavigationActionAllowed(),
                    handleNavigationAction = { effect ->
                        navigator.handleFlowStepOneEffect(route, effect)
                    },
                )
            }
            entry<AppDestination.FlowStepTwo> { route ->
                val viewModel: NavigationFlowStepTwoViewModel = navigationEntryViewModel(
                    key = "flow-step-two:${route.flowId.value}",
                    route.flowId,
                )

                NavigationFlowStepTwoScreen(
                    viewModel = viewModel,
                    isNavigationActionAllowed = rememberIsNavigationActionAllowed(),
                    handleNavigationAction = { effect ->
                        navigator.handleFlowStepTwoEffect(route, effect)
                    },
                )
            }
            entry<AppDestination.ConfirmExperiment>(
                metadata = { DialogSceneStrategy.dialog() },
            ) { route ->
                val viewModel: NavigationConfirmationDialogViewModel = navigationEntryViewModel(
                    key = "confirm-experiment:${route.experimentId.value}",
                    route.experimentId,
                )

                NavigationConfirmationDialogScreen(
                    viewModel = viewModel,
                    isNavigationActionAllowed = rememberIsNavigationActionAllowed(),
                    handleNavigationAction = { effect ->
                        navigator.handleConfirmationEffect(route, effect)
                    },
                )
            }
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = navigationEvents::requestBack,
        entryDecorators = entryDecorators,
        sceneStrategies = listOf(DialogSceneStrategy(), SinglePaneSceneStrategy()),
        entryProvider = entries,
    )
}

/** The complete set of serializable Nav3 keys. Routes contain only typed IDs, never ViewState. */
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

/** Source-compatible factory for the typed navigation result now owned by presentation. */
object ExperimentDialogResult {
    fun Confirmed(experimentId: ExperimentId): NavigationDialogResult =
        NavigationDialogResult.Confirmed(experimentId)

    fun Cancelled(experimentId: ExperimentId): NavigationDialogResult =
        NavigationDialogResult.Cancelled(experimentId)
}

/** Common, host-safe signal for initial and repeated platform deep-link delivery. */
class AppNavigationEventBridge {
    private var nextEventId = 0L
    private var _latestEvent by mutableStateOf<ExternalNavigationEvent?>(null)
    private val backRequestChannel = Channel<Unit>(Channel.UNLIMITED)

    val latestEvent: ExternalNavigationEvent?
        get() = _latestEvent

    val backRequests: Flow<Unit> = backRequestChannel.receiveAsFlow()

    fun accept(rawUrl: String?) {
        nextEventId += 1
        _latestEvent = ExternalNavigationEvent(id = nextEventId, rawUrl = rawUrl)
    }

    /** Clears only the event just handled, preserving a newer live URL delivered concurrently. */
    fun consume(eventId: Long) {
        if (_latestEvent?.id == eventId) {
            _latestEvent = null
        }
    }

    fun requestBack() {
        check(backRequestChannel.trySend(Unit).isSuccess) { "Back request channel is unavailable" }
    }
}

data class ExternalNavigationEvent(
    val id: Long,
    val rawUrl: String?,
)

/** Strict allowlist for external URLs. Every nonmatching form deliberately returns to root. */
object HabitLabDeepLink {
    private const val experimentPrefix = "habitlab://experiment/"

    fun parse(rawUrl: String?): AppDestination.Experiment? {
        val encodedId = rawUrl
            ?.takeIf { it.startsWith(experimentPrefix) }
            ?.removePrefix(experimentPrefix)
            ?.takeIf { value ->
                value.isNotEmpty() && value.none { character ->
                    character == '/' || character == '?' || character == '#'
                }
            }
            ?: return null

        return ExperimentId.fromExternalValue(encodedId)?.let(AppDestination::Experiment)
    }
}

/** Result identity is scoped to the immediate caller after a dialog is popped, never a route field. */
private data class DialogResultDelivery(
    val id: Long,
    val experimentId: ExperimentId,
    val result: NavigationDialogResult,
)

private class AppNavigator(
    private val backStack: NavBackStack<NavKey>,
    private val snapshotStore: NavigationRouteSnapshotStore,
    private val onDialogResult: (DialogResultDelivery) -> Unit,
    private val onNavigationStarted: () -> Unit,
) {
    private var nextDialogResultId = 0L

    suspend fun handleGalleryEffect(effect: GalleryNavigationEffect) {
        if (backStack.lastOrNull() != AppDestination.Gallery) {
            return
        }
        when (effect) {
            is GalleryNavigationEffect.OpenExperiment -> openExperiment(effect.experimentId)
            GalleryNavigationEffect.StartFlow -> startGalleryFlow()
            GalleryNavigationEffect.Back -> onBack()
        }
    }

    suspend fun handleExperimentEffect(
        origin: AppDestination.Experiment,
        effect: ExperimentNavigationEffect,
    ): NavigationDialogResult? {
        if (effect == ExperimentNavigationEffect.PopToRoot) {
            if (origin in backStack) {
                popToRoot()
            }
            return null
        }
        if (backStack.lastOrNull() != origin) {
            return null
        }
        return when (effect) {
            ExperimentNavigationEffect.Back -> {
                onBack()
                null
            }
            is ExperimentNavigationEffect.OpenConfirmation -> {
                openConfirmation(effect.experimentId)
                null
            }
            is ExperimentNavigationEffect.StartFlow -> {
                startExperimentFlow(effect.experimentId)
                null
            }
            is ExperimentNavigationEffect.DialogResultDelivered -> {
                effect.result.takeIf { result -> acceptDialogResult(origin, result) }
            }
            ExperimentNavigationEffect.PopToRoot -> null
        }
    }

    suspend fun handleFlowStepOneEffect(
        origin: AppDestination.FlowStepOne,
        effect: FlowStepOneNavigationEffect,
    ) {
        if (effect == FlowStepOneNavigationEffect.PopToRoot) {
            if (origin in backStack) {
                popToRoot()
            }
            return
        }
        if (backStack.lastOrNull() != origin) {
            return
        }
        when (effect) {
            FlowStepOneNavigationEffect.Back -> onBack()
            is FlowStepOneNavigationEffect.Advance -> advanceFlow(effect.flowId)
            FlowStepOneNavigationEffect.PopToRoot -> Unit
        }
    }

    suspend fun handleFlowStepTwoEffect(
        origin: AppDestination.FlowStepTwo,
        effect: FlowStepTwoNavigationEffect,
    ) {
        if (effect == FlowStepTwoNavigationEffect.PopToRoot) {
            if (origin in backStack) {
                popToRoot()
            }
            return
        }
        if (backStack.lastOrNull() != origin) {
            return
        }
        when (effect) {
            FlowStepTwoNavigationEffect.Back -> onBack()
            is FlowStepTwoNavigationEffect.Complete -> completeFlow(effect.flowId)
            FlowStepTwoNavigationEffect.PopToRoot -> Unit
        }
    }

    suspend fun handleConfirmationEffect(
        origin: AppDestination.ConfirmExperiment,
        effect: ConfirmationNavigationEffect,
    ) {
        if (backStack.lastOrNull() != origin) {
            return
        }
        when (effect) {
            is ConfirmationNavigationEffect.Resolve -> resolveConfirmation(effect.result)
        }
    }

    private suspend fun acceptDialogResult(
        origin: AppDestination.Experiment,
        result: NavigationDialogResult,
    ): Boolean {
        if (backStack.lastOrNull() == origin && result.experimentId == origin.experimentId) {
            return true
        }
        popToRoot()
        return false
    }

    suspend fun onBack() {
        when (val top = backStack.lastOrNull()) {
            is AppDestination.ConfirmExperiment -> {
                resolveConfirmation(NavigationDialogResult.Cancelled(top.experimentId))
            }

            null, AppDestination.Gallery -> Unit
            else -> {
                onNavigationStarted()
                backStack.removeLast()
                persistCompletedStack()
            }
        }
    }

    suspend fun handleExternalNavigation(event: ExternalNavigationEvent) {
        onNavigationStarted()
        replaceWithRoot()
        HabitLabDeepLink.parse(event.rawUrl)?.let(backStack::add)
        persistCompletedStack()
    }

    private suspend fun openExperiment(experimentId: ExperimentId) {
        if (ExperimentId.fromInternalValue(experimentId.value) == null) {
            popToRoot()
            return
        }
        onNavigationStarted()
        backStack += AppDestination.Experiment(experimentId)
        persistCompletedStack()
    }

    private suspend fun startGalleryFlow() {
        if (backStack.lastOrNull() != AppDestination.Gallery) {
            popToRoot()
            return
        }
        onNavigationStarted()
        backStack += AppDestination.FlowStepOne(FlowId.gallerySetup())
        persistCompletedStack()
    }

    private suspend fun startExperimentFlow(experimentId: ExperimentId) {
        if (backStack.lastOrNull() != AppDestination.Experiment(experimentId)) {
            popToRoot()
            return
        }
        onNavigationStarted()
        backStack += AppDestination.FlowStepOne(FlowId.forExperiment(experimentId))
        persistCompletedStack()
    }

    private suspend fun advanceFlow(flowId: FlowId) {
        if (!FlowId.isSupported(flowId) || backStack.lastOrNull() != AppDestination.FlowStepOne(flowId)) {
            popToRoot()
            return
        }
        onNavigationStarted()
        backStack += AppDestination.FlowStepTwo(flowId)
        persistCompletedStack()
    }

    private suspend fun completeFlow(flowId: FlowId) {
        val expectedStepOne = AppDestination.FlowStepOne(flowId)
        if (
            backStack.lastOrNull() != AppDestination.FlowStepTwo(flowId) ||
            backStack.getOrNull(backStack.lastIndex - 1) != expectedStepOne
        ) {
            popToRoot()
            return
        }
        onNavigationStarted()
        backStack.removeLast()
        backStack.removeLast()
        persistCompletedStack()
    }

    private suspend fun openConfirmation(experimentId: ExperimentId) {
        if (backStack.lastOrNull() != AppDestination.Experiment(experimentId)) {
            popToRoot()
            return
        }
        onNavigationStarted()
        backStack += AppDestination.ConfirmExperiment(experimentId)
        persistCompletedStack()
    }

    private suspend fun resolveConfirmation(result: NavigationDialogResult) {
        val dialog = backStack.lastOrNull() as? AppDestination.ConfirmExperiment
        val caller = backStack.getOrNull(backStack.lastIndex - 1) as? AppDestination.Experiment
        if (dialog?.experimentId != result.experimentId || caller?.experimentId != result.experimentId) {
            popToRoot()
            return
        }
        backStack.removeLast()
        nextDialogResultId += 1
        onDialogResult(
            DialogResultDelivery(
                id = nextDialogResultId,
                experimentId = caller.experimentId,
                result = result,
            ),
        )
        persistCompletedStack()
    }

    private suspend fun popToRoot() {
        onNavigationStarted()
        replaceWithRoot()
        persistCompletedStack()
    }

    private fun replaceWithRoot() {
        backStack.clear()
        backStack += AppDestination.Gallery
    }

    /** Persists only complete, validated post-operation stacks; transient removals are never saved. */
    private suspend fun persistCompletedStack() = withContext(NonCancellable) {
        val destinations = backStack.map { it as? AppDestination }
        if (destinations.any { it == null }) {
            snapshotStore.clear()
        } else {
            NavigationRouteSnapshotCodec.persist(snapshotStore, destinations.filterNotNull())
        }
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

private fun NavigationDialogResult.toDisplay(): NavigationDialogResultDisplay = when (this) {
    is NavigationDialogResult.Confirmed -> NavigationDialogResultDisplay.Confirmed
    is NavigationDialogResult.Cancelled -> NavigationDialogResultDisplay.Cancelled
}

internal fun NavigationDialogResult?.displayFor(
    experimentId: ExperimentId,
): NavigationDialogResultDisplay? = takeIf { it?.experimentId == experimentId }?.toDisplay()
