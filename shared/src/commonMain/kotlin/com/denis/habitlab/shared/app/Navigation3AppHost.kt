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
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.denis.habitlab.shared.presentation.confirmdelete.ConfirmDeleteScreen
import com.denis.habitlab.shared.presentation.confirmdelete.ConfirmDeleteViewModel
import com.denis.habitlab.shared.presentation.dailycheckin.DailyCheckInScreen
import com.denis.habitlab.shared.presentation.dailycheckin.DailyCheckInViewModel
import com.denis.habitlab.shared.presentation.experimentdetails.ExperimentDetailsScreen
import com.denis.habitlab.shared.presentation.experimentdetails.ExperimentDetailsViewModel
import com.denis.habitlab.shared.presentation.experimenteditor.ExperimentEditorScreen
import com.denis.habitlab.shared.presentation.experimenteditor.ExperimentEditorViewModel
import com.denis.habitlab.shared.presentation.experimentlist.ExperimentListScreen
import com.denis.habitlab.shared.presentation.experimentlist.ExperimentListViewModel
import com.denis.habitlab.shared.presentation.metricpicker.MetricPickerScreen
import com.denis.habitlab.shared.presentation.metricpicker.MetricPickerViewModel
import com.denis.habitlab.shared.presentation.navigation.DeleteDialogResult
import com.denis.habitlab.shared.presentation.navigation.ExperimentEditorEntryArguments
import com.denis.habitlab.shared.presentation.navigation.MetricPickerEntryArguments
import com.denis.habitlab.shared.presentation.navigation.ExperimentDialogResult as LegacyExperimentDialogResult
import com.denis.habitlab.shared.presentation.navigation.experiment.NavigationDialogResultDisplay
import com.denis.habitlab.shared.presentation.navigation.MetricPickerResult
import com.denis.habitlab.shared.presentation.settings.SettingsScreen
import com.denis.habitlab.shared.presentation.settings.SettingsViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/** Common owner of the one Nav3 stack for both platforms and every typed dialog result. */
@Composable
internal fun Navigation3AppHost(
    appTitle: String,
    navigationEvents: AppNavigationEventBridge,
) {
    val snapshotStore = rememberNavigationRouteSnapshotStore()
    val restored = remember(snapshotStore) { NavigationRouteSnapshotCodec.restore(snapshotStore.read()) }
    val backStack = rememberNavBackStack(navigationSavedStateConfiguration, *restored.routes.toTypedArray())
    var pendingResult by remember { mutableStateOf<DialogResultDelivery?>(null) }
    var confirmDeleteDismissalLock by remember { mutableStateOf(ConfirmDeleteDismissalLock.UNLOCKED) }
    val navigator = remember(backStack, snapshotStore) {
        AppNavigator(
            backStack = backStack,
            snapshotStore = snapshotStore,
            onDialogResult = { pendingResult = it },
            confirmDeleteDismissalLock = { confirmDeleteDismissalLock },
            onNavigationStarted = {
                pendingResult = null
                confirmDeleteDismissalLock = ConfirmDeleteDismissalLock.UNLOCKED
            },
        )
    }
    val navigationEvent = navigationEvents.latestEvent

    LaunchedEffect(restored.shouldClearStoredSnapshot, snapshotStore) {
        if (restored.shouldClearStoredSnapshot) snapshotStore.clear()
    }
    LaunchedEffect(navigationEvent?.id) {
        navigationEvent?.let { event ->
            navigator.handleExternalNavigation(event)
            navigationEvents.consume(event.id)
        }
    }
    LaunchedEffect(navigationEvents, navigator) {
        navigationEvents.backRequests.collect { withContext(NonCancellable) { navigator.onBack() } }
    }

    val decorators = listOf(
        androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
        rememberViewModelStoreNavEntryDecorator<NavKey>(),
    )
    val entries = remember(appTitle, navigator, pendingResult) {
        entryProvider<NavKey> {
            entry<AppDestination.Gallery> {
                val viewModel: ExperimentListViewModel = navigationEntryViewModel(key = "experiment-list")
                ExperimentListScreen(
                    viewModel = viewModel,
                    isNavigationActionAllowed = rememberIsNavigationActionAllowed(),
                    handleNavigationAction = navigator::handleListEffect,
                )
            }
            entry<AppDestination.Experiment> { route ->
                val viewModel: ExperimentDetailsViewModel = navigationEntryViewModel(
                    key = "experiment-details:${route.experimentId.value}", route.experimentId,
                )
                val delivery = pendingResult?.takeIf { it.caller == route }
                val deleteResult = (delivery?.result as? DialogResult.Delete)?.value
                ExperimentDetailsScreen(
                    viewModel = viewModel,
                    deliveredDeleteResult = deleteResult,
                    onDeleteResultConsumed = {
                        if (pendingResult?.id == delivery?.id) pendingResult = null
                    },
                    isNavigationActionAllowed = rememberIsNavigationActionAllowed(),
                    handleNavigationAction = { navigator.handleDetailsEffect(route, it) },
                )
            }
            entry<AppDestination.ExperimentEditor> { route ->
                val viewModel: ExperimentEditorViewModel = navigationEntryViewModel(
                    key = "experiment-editor:${route.experimentId?.value ?: "new"}",
                    ExperimentEditorEntryArguments(route.experimentId),
                )
                val delivery = pendingResult?.takeIf { it.caller == route }
                val metricResult = (delivery?.result as? DialogResult.Metric)?.value
                ExperimentEditorScreen(
                    viewModel = viewModel,
                    deliveredMetricResult = metricResult,
                    onMetricResultConsumed = {
                        if (pendingResult?.id == delivery?.id) pendingResult = null
                    },
                    isNavigationActionAllowed = rememberIsNavigationActionAllowed(),
                    handleNavigationAction = { navigator.handleEditorEffect(route, it) },
                )
            }
            entry<AppDestination.DailyCheckIn> { route ->
                val viewModel: DailyCheckInViewModel = navigationEntryViewModel(
                    key = "daily-check-in:${route.experimentId.value}:${route.localDate.value}",
                    route.experimentId,
                    route.localDate.toLocalDate(),
                )
                DailyCheckInScreen(
                    viewModel = viewModel,
                    isNavigationActionAllowed = rememberIsNavigationActionAllowed(),
                    handleNavigationAction = { navigator.handleCheckInEffect(route, it) },
                )
            }
            entry<AppDestination.Settings> {
                val viewModel: SettingsViewModel = navigationEntryViewModel(key = "settings")
                SettingsScreen(
                    viewModel = viewModel,
                    isNavigationActionAllowed = rememberIsNavigationActionAllowed(),
                    handleNavigationAction = navigator::handleSettingsEffect,
                )
            }
            entry<AppDestination.MetricPicker>(metadata = { DialogSceneStrategy.dialog() }) { route ->
                val viewModel: MetricPickerViewModel = navigationEntryViewModel(
                    key = "metric-picker:${route.experimentId?.value ?: "new"}",
                    MetricPickerEntryArguments(route.experimentId),
                )
                MetricPickerScreen(
                    viewModel = viewModel,
                    isNavigationActionAllowed = rememberIsNavigationActionAllowed(),
                    handleNavigationAction = { navigator.handleMetricPickerEffect(route, it) },
                )
            }
            entry<AppDestination.ConfirmDelete>(metadata = { DialogSceneStrategy.dialog() }) { route ->
                val viewModel: ConfirmDeleteViewModel = navigationEntryViewModel(
                    key = "confirm-delete:${route.experimentId.value}", route.experimentId,
                )
                ConfirmDeleteScreen(
                    viewModel = viewModel,
                    onDismissalLockChanged = { isLocked ->
                        if (backStack.lastOrNull() == route) {
                            confirmDeleteDismissalLock = if (isLocked) {
                                ConfirmDeleteDismissalLock.LOCKED
                            } else {
                                ConfirmDeleteDismissalLock.UNLOCKED
                            }
                        }
                    },
                    isNavigationActionAllowed = rememberIsNavigationActionAllowed(),
                    handleNavigationAction = { navigator.handleConfirmDeleteEffect(route, it) },
                )
            }
        }
    }
    NavDisplay(
        backStack = backStack,
        onBack = navigationEvents::requestBack,
        entryDecorators = decorators,
        sceneStrategies = listOf(DialogSceneStrategy(), SinglePaneSceneStrategy()),
        entryProvider = entries,
    )
}

/** Complete typed route set. Gallery is retained as the stable, safe root wire key. */
@Serializable
sealed interface AppDestination : NavKey {
    @Serializable data object Gallery : AppDestination
    @Serializable data class Experiment(val experimentId: ExperimentId) : AppDestination
    @Serializable data class ExperimentEditor(val experimentId: ExperimentId?) : AppDestination
    @Serializable data class DailyCheckIn(val experimentId: ExperimentId, val localDate: CheckInRouteDate) : AppDestination
    @Serializable data object Settings : AppDestination
    @Serializable data class MetricPicker(val experimentId: ExperimentId?) : AppDestination
    @Serializable data class ConfirmDelete(val experimentId: ExperimentId) : AppDestination
}

/** Serializable value object for LocalDate-compatible route persistence without a UI snapshot. */
@Serializable
data class CheckInRouteDate(val value: String) {
    init { require(runCatching { LocalDate.parse(value) }.isSuccess) { "Invalid check-in local date: $value" } }

    fun toLocalDate(): LocalDate = LocalDate.parse(value)

    companion object { fun from(localDate: LocalDate) = CheckInRouteDate(localDate.toString()) }
}

class AppNavigationEventBridge {
    private var nextEventId = 0L
    private var latest by mutableStateOf<ExternalNavigationEvent?>(null)
    private val backChannel = Channel<Unit>(Channel.UNLIMITED)
    val latestEvent: ExternalNavigationEvent? get() = latest
    val backRequests: Flow<Unit> = backChannel.receiveAsFlow()
    fun accept(rawUrl: String?) { nextEventId += 1; latest = ExternalNavigationEvent(nextEventId, rawUrl) }
    fun consume(eventId: Long) { if (latest?.id == eventId) latest = null }
    fun requestBack() { check(backChannel.trySend(Unit).isSuccess) { "Back request channel is unavailable" } }
}

data class ExternalNavigationEvent(val id: Long, val rawUrl: String?)

/** External contract deliberately remains limited to the two shipped experiment identifiers. */
object HabitLabDeepLink {
    private const val experimentPrefix = "habitlab://experiment/"
    fun parse(rawUrl: String?): AppDestination.Experiment? {
        val value = rawUrl?.takeIf { it.startsWith(experimentPrefix) }?.removePrefix(experimentPrefix)
            ?.takeIf { it.isNotEmpty() && it.none { char -> char == '/' || char == '?' || char == '#' } }
            ?: return null
        return ExperimentId.fromExternalValue(value)?.let(AppDestination::Experiment)
    }
}

/** Compatibility factory for the original confirmation result contract kept for DEN-10 callers. */
object ExperimentDialogResult {
    fun Confirmed(experimentId: ExperimentId): LegacyExperimentDialogResult =
        LegacyExperimentDialogResult.Confirmed(experimentId)

    fun Cancelled(experimentId: ExperimentId): LegacyExperimentDialogResult =
        LegacyExperimentDialogResult.Cancelled(experimentId)
}

internal fun LegacyExperimentDialogResult?.displayFor(experimentId: ExperimentId): NavigationDialogResultDisplay? =
    when (this?.takeIf { it.experimentId == experimentId }) {
        is LegacyExperimentDialogResult.Confirmed -> NavigationDialogResultDisplay.Confirmed
        is LegacyExperimentDialogResult.Cancelled -> NavigationDialogResultDisplay.Cancelled
        null -> null
    }

private sealed interface DialogResult {
    data class Metric(val value: MetricPickerResult) : DialogResult
    data class Delete(val value: DeleteDialogResult) : DialogResult
}

private data class DialogResultDelivery(val id: Long, val caller: AppDestination, val result: DialogResult)

private class AppNavigator(
    private val backStack: NavBackStack<NavKey>,
    private val snapshotStore: NavigationRouteSnapshotStore,
    private val onDialogResult: (DialogResultDelivery) -> Unit,
    private val confirmDeleteDismissalLock: () -> ConfirmDeleteDismissalLock,
    private val onNavigationStarted: () -> Unit,
) {
    private var nextResultId = 0L

    suspend fun handleListEffect(effect: com.denis.habitlab.shared.presentation.experimentlist.NavigationEffect) {
        if (backStack.lastOrNull() != AppDestination.Gallery) return
        when (effect) {
            is com.denis.habitlab.shared.presentation.experimentlist.NavigationEffect.OpenDetails -> openDetails(effect.experimentId)
            com.denis.habitlab.shared.presentation.experimentlist.NavigationEffect.OpenCreateEditor -> add(AppDestination.ExperimentEditor(null))
            com.denis.habitlab.shared.presentation.experimentlist.NavigationEffect.OpenSettings -> add(AppDestination.Settings)
        }
    }

    suspend fun handleDetailsEffect(
        origin: AppDestination.Experiment,
        effect: com.denis.habitlab.shared.presentation.experimentdetails.NavigationEffect,
    ) {
        if (effect == com.denis.habitlab.shared.presentation.experimentdetails.NavigationEffect.PopToRoot) {
            // A Room delete can make the underlying observer report Missing before its dialog's
            // confirmed effect is delivered. Preserve the pop-then-result dialog invariant.
            if (
                backStack.lastOrNull() is AppDestination.ConfirmDelete &&
                backStack.getOrNull(backStack.lastIndex - 1) == origin
            ) return
            if (origin in backStack) popToRoot()
            return
        }
        if (backStack.lastOrNull() != origin) return
        when (effect) {
            com.denis.habitlab.shared.presentation.experimentdetails.NavigationEffect.Back -> onBack()
            is com.denis.habitlab.shared.presentation.experimentdetails.NavigationEffect.OpenEditor -> add(AppDestination.ExperimentEditor(effect.experimentId))
            is com.denis.habitlab.shared.presentation.experimentdetails.NavigationEffect.OpenDailyCheckIn ->
                add(AppDestination.DailyCheckIn(effect.experimentId, CheckInRouteDate.from(effect.localDate)))
            is com.denis.habitlab.shared.presentation.experimentdetails.NavigationEffect.OpenConfirmDelete ->
                add(AppDestination.ConfirmDelete(effect.experimentId))
            com.denis.habitlab.shared.presentation.experimentdetails.NavigationEffect.PopToRoot -> Unit
        }
    }

    suspend fun handleEditorEffect(
        origin: AppDestination.ExperimentEditor,
        effect: com.denis.habitlab.shared.presentation.experimenteditor.NavigationEffect,
    ) {
        if (effect == com.denis.habitlab.shared.presentation.experimenteditor.NavigationEffect.PopToRoot) {
            if (origin in backStack) popToRoot()
            return
        }
        if (backStack.lastOrNull() != origin) return
        when (effect) {
            com.denis.habitlab.shared.presentation.experimenteditor.NavigationEffect.Back -> onBack()
            is com.denis.habitlab.shared.presentation.experimenteditor.NavigationEffect.OpenMetricPicker ->
                add(AppDestination.MetricPicker(effect.experimentId))
            is com.denis.habitlab.shared.presentation.experimenteditor.NavigationEffect.SaveComplete -> completeEditor(origin, effect.experimentId)
            com.denis.habitlab.shared.presentation.experimenteditor.NavigationEffect.PopToRoot -> Unit
        }
    }

    suspend fun handleCheckInEffect(
        origin: AppDestination.DailyCheckIn,
        effect: com.denis.habitlab.shared.presentation.dailycheckin.NavigationEffect,
    ) {
        if (effect == com.denis.habitlab.shared.presentation.dailycheckin.NavigationEffect.PopToRoot) {
            if (origin in backStack) popToRoot()
        } else if (backStack.lastOrNull() == origin && effect == com.denis.habitlab.shared.presentation.dailycheckin.NavigationEffect.Back) {
            onBack()
        }
    }

    suspend fun handleSettingsEffect(effect: com.denis.habitlab.shared.presentation.settings.NavigationEffect) {
        if (backStack.lastOrNull() == AppDestination.Settings && effect == com.denis.habitlab.shared.presentation.settings.NavigationEffect.Back) onBack()
    }

    suspend fun handleMetricPickerEffect(
        origin: AppDestination.MetricPicker,
        effect: com.denis.habitlab.shared.presentation.metricpicker.NavigationEffect,
    ) {
        if (backStack.lastOrNull() == origin && effect is com.denis.habitlab.shared.presentation.metricpicker.NavigationEffect.Resolve) {
            resolveMetric(origin, effect.result)
        }
    }

    suspend fun handleConfirmDeleteEffect(
        origin: AppDestination.ConfirmDelete,
        effect: com.denis.habitlab.shared.presentation.confirmdelete.NavigationEffect,
    ) {
        if (backStack.lastOrNull() == origin && effect is com.denis.habitlab.shared.presentation.confirmdelete.NavigationEffect.Resolve) {
            resolveDelete(origin, effect.result)
        }
    }

    suspend fun onBack() {
        when (val top = backStack.lastOrNull()) {
            is AppDestination.MetricPicker -> resolveMetric(top, MetricPickerResult.Cancelled(top.experimentId))
            is AppDestination.ConfirmDelete -> {
                when (ConfirmDeleteDismissalPolicy.decide(confirmDeleteDismissalLock())) {
                    ConfirmDeleteDismissalDecision.Ignore -> Unit
                    ConfirmDeleteDismissalDecision.ResolveCancelled -> {
                        resolveDelete(top, DeleteDialogResult.Cancelled(top.experimentId))
                    }
                }
            }
            null, AppDestination.Gallery -> Unit
            else -> {
                onNavigationStarted()
                backStack.removeLast()
                persist()
            }
        }
    }

    suspend fun handleExternalNavigation(event: ExternalNavigationEvent) {
        onNavigationStarted()
        replaceWithRoot()
        HabitLabDeepLink.parse(event.rawUrl)?.let(backStack::add)
        persist()
    }

    private suspend fun openDetails(experimentId: ExperimentId) {
        if (ExperimentId.fromInternalValue(experimentId.value) == null) popToRoot() else add(AppDestination.Experiment(experimentId))
    }

    private suspend fun add(destination: AppDestination) {
        onNavigationStarted()
        backStack += destination
        persist()
    }

    private suspend fun completeEditor(origin: AppDestination.ExperimentEditor, experimentId: ExperimentId) {
        if (origin.experimentId != null && origin.experimentId != experimentId) { popToRoot(); return }
        onNavigationStarted()
        backStack.removeLast()
        if (origin.experimentId == null) backStack += AppDestination.Experiment(experimentId)
        persist()
    }

    private suspend fun resolveMetric(dialog: AppDestination.MetricPicker, result: MetricPickerResult) {
        val caller = backStack.getOrNull(backStack.lastIndex - 1) as? AppDestination.ExperimentEditor
        if (caller == null || caller.experimentId != dialog.experimentId || result.experimentId != dialog.experimentId) { popToRoot(); return }
        resolve(caller, DialogResult.Metric(result))
    }

    private suspend fun resolveDelete(dialog: AppDestination.ConfirmDelete, result: DeleteDialogResult) {
        val caller = backStack.getOrNull(backStack.lastIndex - 1) as? AppDestination.Experiment
        if (caller?.experimentId != dialog.experimentId || result.experimentId != dialog.experimentId) { popToRoot(); return }
        resolve(caller, DialogResult.Delete(result))
    }

    private suspend fun resolve(caller: AppDestination, result: DialogResult) {
        onNavigationStarted()
        backStack.removeLast()
        nextResultId += 1
        onDialogResult(DialogResultDelivery(nextResultId, caller, result))
        persist()
    }

    private suspend fun popToRoot() {
        onNavigationStarted()
        replaceWithRoot()
        persist()
    }

    private fun replaceWithRoot() { backStack.clear(); backStack += AppDestination.Gallery }

    private suspend fun persist() = withContext(NonCancellable) {
        val routes = backStack.map { it as? AppDestination }
        if (routes.any { it == null }) snapshotStore.clear()
        else NavigationRouteSnapshotCodec.persist(snapshotStore, routes.filterNotNull())
    }
}

private val navigationSavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(AppDestination.Gallery::class, AppDestination.Gallery.serializer())
            subclass(AppDestination.Experiment::class, AppDestination.Experiment.serializer())
            subclass(AppDestination.ExperimentEditor::class, AppDestination.ExperimentEditor.serializer())
            subclass(AppDestination.DailyCheckIn::class, AppDestination.DailyCheckIn.serializer())
            subclass(AppDestination.Settings::class, AppDestination.Settings.serializer())
            subclass(AppDestination.MetricPicker::class, AppDestination.MetricPicker.serializer())
            subclass(AppDestination.ConfirmDelete::class, AppDestination.ConfirmDelete.serializer())
        }
    }
}
