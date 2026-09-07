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
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
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
import com.denis.habitlab.shared.presentation.launchgate.LaunchGateScreen
import com.denis.habitlab.shared.presentation.launchgate.LaunchGateViewModel
import com.denis.habitlab.shared.presentation.launchgate.LaunchGateDecision
import com.denis.habitlab.shared.presentation.onboardingcheckpoint.OnboardingCompletionMonitor
import com.denis.habitlab.shared.presentation.onboardingcheckpoint.OnboardingCheckpointScreen
import com.denis.habitlab.shared.presentation.onboardingcheckpoint.OnboardingCheckpointViewModel
import com.denis.habitlab.shared.presentation.navigation.DeleteDialogResult
import com.denis.habitlab.shared.presentation.navigation.ExperimentEditorEntryArguments
import com.denis.habitlab.shared.presentation.navigation.MetricPickerEntryArguments
import com.denis.habitlab.shared.presentation.navigation.OnboardingStep
import com.denis.habitlab.shared.presentation.navigation.ExperimentDialogResult as LegacyExperimentDialogResult
import com.denis.habitlab.shared.presentation.navigation.experiment.NavigationDialogResultDisplay
import com.denis.habitlab.shared.presentation.navigation.MetricPickerResult
import com.denis.habitlab.shared.presentation.settings.SettingsScreen
import com.denis.habitlab.shared.presentation.settings.SettingsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Common owner of the one Nav3 stack for both platforms and every typed dialog result. */
@Composable
internal fun Navigation3AppHost(
    appTitle: String,
    navigationEvents: AppNavigationEventBridge,
) {
    val snapshotStore = rememberNavigationRouteSnapshotStore()
    val restored = remember(snapshotStore) { NavigationRouteSnapshotCodec.restore(snapshotStore.read()) }
    // This monitor starts after any incomplete LaunchGate decision. It is app-owned rather than
    // entry-owned so a Back removal cannot cancel durable completion delivery.
    val onboardingCompletionMonitor: OnboardingCompletionMonitor = appCompositionDependency()
    val onboardingCompletionRequests = remember { OnboardingCompletionObservationRequests() }
    // Do not use rememberNavBackStack: its saveable restoration could bypass this cold LaunchGate.
    // The v3 custom snapshot remains only a post-decision candidate.
    val backStack = remember { NavBackStack<NavKey>(*restored.routes.toTypedArray()) }
    var pendingResult by remember { mutableStateOf<DialogResultDelivery?>(null) }
    var confirmDeleteDismissalLock by remember { mutableStateOf(ConfirmDeleteDismissalLock.UNLOCKED) }
    val navigator = remember(backStack, snapshotStore, restored.shouldClearStoredSnapshot, onboardingCompletionRequests) {
        AppNavigator(
            backStack = backStack,
            snapshotStore = snapshotStore,
            initialSnapshotCleanupPending = restored.shouldClearStoredSnapshot,
            onDialogResult = { pendingResult = it },
            confirmDeleteDismissalLock = { confirmDeleteDismissalLock },
            onNavigationStarted = {
                pendingResult = null
                confirmDeleteDismissalLock = ConfirmDeleteDismissalLock.UNLOCKED
            },
            onIncompleteOnboardingObservationRequested = onboardingCompletionRequests::request,
        )
    }
    LaunchedEffect(navigationEvents, navigator) {
        navigationEvents.externalNavigationEvents.collect { event ->
            navigator.deferExternalNavigation(event)
            navigationEvents.consume(event.id)
        }
    }
    LaunchedEffect(navigationEvents, navigator) {
        navigationEvents.backRequests.collect { withContext(NonCancellable) { navigator.onBack() } }
    }
    LaunchedEffect(navigator, onboardingCompletionMonitor, onboardingCompletionRequests) {
        onboardingCompletionRequests.requests.collect {
            onboardingCompletionMonitor.observeCompletion().collect { completion ->
                navigator.handleOnboardingEffect(
                    AppDestination.OnboardingCheckpoint(OnboardingStep.SETUP),
                    completion,
                )
            }
        }
    }

    val decorators = listOf(
        androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
        rememberViewModelStoreNavEntryDecorator<NavKey>(),
    )
    val entries = remember(appTitle, navigator, pendingResult, restored.candidateRoutes) {
        entryProvider<NavKey> {
            entry<AppDestination.LaunchGate> {
                val viewModel: LaunchGateViewModel = navigationEntryViewModel(key = "launch-gate")
                LaunchGateScreen(
                    viewModel = viewModel,
                    handleNavigationAction = { effect: com.denis.habitlab.shared.presentation.launchgate.NavigationEffect ->
                        when (effect) {
                            is com.denis.habitlab.shared.presentation.launchgate.NavigationEffect.Resolve ->
                                if (!navigator.handleLaunchGateEffect(effect.decision, restored.candidateRoutes)) {
                                    viewModel.onRoutePersistenceFailed()
                                }
                            com.denis.habitlab.shared.presentation.launchgate.NavigationEffect.ClearObsoleteSnapshot ->
                                if (!navigator.clearObsoleteSnapshotAfterBlockedLaunch()) {
                                    viewModel.onRoutePersistenceFailed()
                                }
                        }
                    },
                )
            }
            // Neutral entry contours only. DEN-26/25/27/29/30/31/28 replace these one by one.
            entry<AppDestination.Welcome> {
                val viewModel: OnboardingCheckpointViewModel = navigationEntryViewModel(
                    key = "onboarding:${OnboardingStep.WELCOME}", OnboardingStep.WELCOME,
                )
                OnboardingCheckpointScreen(
                    viewModel = viewModel,
                    isNavigationActionAllowed = rememberIsNavigationActionAllowed(),
                    handleNavigationAction = { navigator.handleOnboardingEffect(AppDestination.Welcome, it) },
                )
            }
            entry<AppDestination.OnboardingCheckpoint> { route ->
                val viewModel: OnboardingCheckpointViewModel = navigationEntryViewModel(
                    key = "onboarding:${route.step}", route.step,
                )
                OnboardingCheckpointScreen(
                    viewModel = viewModel,
                    isNavigationActionAllowed = rememberIsNavigationActionAllowed(),
                    handleNavigationAction = { navigator.handleOnboardingEffect(route, it) },
                )
            }
            entry<AppDestination.Today> {
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

/** Complete typed route set. LaunchGate is only an in-memory cold root and is never snapshotted. */
@Serializable
sealed interface AppDestination : NavKey {
    @Serializable data object LaunchGate : AppDestination
    @Serializable data object Welcome : AppDestination
    @Serializable data class OnboardingCheckpoint(val step: OnboardingStep) : AppDestination
    @Serializable data object Today : AppDestination
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
    private val externalNavigationChannel = Channel<ExternalNavigationEvent>(Channel.UNLIMITED)
    private val backChannel = Channel<Unit>(Channel.UNLIMITED)
    /** FIFO custody for every native URL delivery; collection is deliberately sequential. */
    val externalNavigationEvents: Flow<ExternalNavigationEvent> = externalNavigationChannel.receiveAsFlow()
    val latestEvent: ExternalNavigationEvent? get() = latest
    val backRequests: Flow<Unit> = backChannel.receiveAsFlow()
    fun accept(rawUrl: String?) {
        nextEventId += 1
        val event = ExternalNavigationEvent(nextEventId, rawUrl)
        latest = event
        check(externalNavigationChannel.trySend(event).isSuccess) { "External navigation channel is unavailable" }
    }
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

/** App-lifetime trigger for the post-gate incomplete-onboarding completion monitor. */
private class OnboardingCompletionObservationRequests {
    private val requestsChannel = Channel<Unit>(Channel.CONFLATED)
    val requests: Flow<Unit> = requestsChannel.receiveAsFlow()

    fun request() {
        check(requestsChannel.trySend(Unit).isSuccess) { "Setup completion request channel is unavailable" }
    }
}

/**
 * Serializes external URL delivery with the durable transition that first admits Today.
 *
 * The command mutex deliberately wraps the initial Today persistence and every replayed URL, so
 * a URL arriving while that persistence suspends cannot overtake an older deferred URL. Its
 * callbacks run under the navigator mutation transaction, so the lock order is command →
 * mutation → snapshot and never the reverse.
 */
internal class DeferredExternalNavigationCoordinator(
    private val runNavigationAtomically: suspend (block: suspend () -> Unit) -> Unit = { block -> block() },
) {
    private val commandMutex = Mutex()
    private val deferredEvents = mutableListOf<ExternalNavigationEvent>()
    private var todayAdmission = TodayAdmission.INELIGIBLE

    suspend fun deferOrProcess(
        event: ExternalNavigationEvent,
        processTodayEvent: suspend (ExternalNavigationEvent) -> Unit,
    ) = commandMutex.withLock {
        if (todayAdmission != TodayAdmission.ELIGIBLE) {
            deferredEvents += event
        } else {
            processTodayEvent(event)
        }
    }

    /**
     * Makes Today eligible only after [prepareToday] has durably persisted its initial route, then
     * replays all older deferred URLs. [commitToday] makes the staged route visible only after
     * that complete FIFO has succeeded and before later arrivals can navigate.
     */
    suspend fun admitToday(
        prepareToday: suspend () -> Unit,
        replayDeferredEvent: suspend (ExternalNavigationEvent) -> Unit,
        commitToday: () -> Unit,
    ) = commandMutex.withLock {
        if (todayAdmission == TodayAdmission.INELIGIBLE) {
            todayAdmission = TodayAdmission.TRANSITIONING
            try {
                runNavigationAtomically {
                    prepareToday()
                    // Preserve the whole FIFO until every replay has succeeded. A later staged
                    // route persistence can fail after an earlier one succeeded, so removing a
                    // prefix here would make the retry lose its canonical ordering.
                    for (event in deferredEvents.toList()) {
                        replayDeferredEvent(event)
                    }
                    commitToday()
                }
                deferredEvents.clear()
                todayAdmission = TodayAdmission.ELIGIBLE
            } catch (error: Throwable) {
                // Retain custody of the complete FIFO for a retryable admission.
                todayAdmission = TodayAdmission.INELIGIBLE
                throw error
            }
        }
    }

    private enum class TodayAdmission {
        INELIGIBLE,
        TRANSITIONING,
        ELIGIBLE,
    }
}

/** Single owner for staged-persist then visible-publish navigation mutations. */
internal class NavigationMutationMutex {
    private val mutex = Mutex()

    suspend fun <T> runAtomically(block: suspend () -> T): T = mutex.withLock { block() }
}

/** The only normal-mutation ordering: staged snapshot attempt first, visible state second. */
internal suspend fun persistThenPublish(
    persistStagedRoute: suspend () -> Unit,
    publishVisibleRoute: () -> Unit,
) {
    persistStagedRoute()
    publishVisibleRoute()
}

/** Clears stale persistence rather than treating a non-product NavKey as a route. */
internal suspend fun appDestinationRoutesOrClear(
    stack: List<NavKey>,
    clearSnapshot: suspend () -> Unit,
): List<AppDestination>? {
    val routes = stack.map { it as? AppDestination }
    if (routes.any { it == null }) {
        clearSnapshot()
        return null
    }
    return routes.filterNotNull()
}

/** Bounded delay policy for retrying only a failed post-onboarding snapshot admission. */
internal interface TodayAdmissionRetryPolicy {
    val maximumAttempts: Int

    suspend fun awaitRetryAfterFailure(failedAttempt: Int)
}

internal object DefaultTodayAdmissionRetryPolicy : TodayAdmissionRetryPolicy {
    override val maximumAttempts: Int = 2

    override suspend fun awaitRetryAfterFailure(failedAttempt: Int) {
        delay(RETRY_DELAY_MILLIS)
    }

    private const val RETRY_DELAY_MILLIS = 250L
}

/** Runs a finite admission retry loop; callers supply a rollback-safe attempt. */
internal suspend fun retryTodayAdmission(
    retryPolicy: TodayAdmissionRetryPolicy,
    attemptAdmission: suspend () -> Boolean,
): Boolean {
    require(retryPolicy.maximumAttempts > 0) { "Today admission retry policy must allow an attempt" }
    repeat(retryPolicy.maximumAttempts) { index ->
        if (attemptAdmission()) return true
        if (index + 1 < retryPolicy.maximumAttempts) retryPolicy.awaitRetryAfterFailure(index + 1)
    }
    return false
}

/** Keeps a failed staged Today admission retryable while resetting caller-owned staging state. */
internal suspend fun DeferredExternalNavigationCoordinator.admitTodayOrRollback(
    prepareToday: suspend () -> Unit,
    rollback: () -> Unit,
    replayDeferredEvent: suspend (ExternalNavigationEvent) -> Unit,
    commitToday: () -> Unit,
): Boolean = try {
    admitToday(prepareToday, replayDeferredEvent, commitToday)
    true
} catch (error: Throwable) {
    if (error is CancellationException) throw error
    rollback()
    false
}

/** Cold LaunchGate admission is reset after failure while its original entry remains mounted. */
internal class ColdLaunchGateAdmission {
    private var launchResolved = false

    fun mayResolve(isLaunchGateCurrent: Boolean): Boolean = isLaunchGateCurrent && !launchResolved

    suspend fun resolve(
        prepareResolution: suspend () -> Unit,
        restoreLaunchGate: () -> Unit,
    ): Boolean {
        launchResolved = true
        return try {
            prepareResolution()
            true
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            restoreLaunchGate()
            launchResolved = false
            false
        }
    }
}

/** Applies entry-scoped Back but admits durable SETUP completion even after that entry is popped. */
internal class OnboardingNavigationEffectHandler(
    private val isOriginCurrent: (AppDestination) -> Boolean,
    private val onBack: suspend () -> Unit,
    private val onSetupCompleted: suspend () -> Unit,
) {
    suspend fun handle(
        origin: AppDestination,
        effect: com.denis.habitlab.shared.presentation.onboardingcheckpoint.NavigationEffect,
    ) {
        when (effect) {
            com.denis.habitlab.shared.presentation.onboardingcheckpoint.NavigationEffect.Back -> {
                if (isOriginCurrent(origin)) onBack()
            }
            com.denis.habitlab.shared.presentation.onboardingcheckpoint.NavigationEffect.OnboardingCompleted -> {
                val setup = origin as? AppDestination.OnboardingCheckpoint
                if (setup?.step == OnboardingStep.SETUP) onSetupCompleted()
            }
        }
    }
}

private class AppNavigator(
    private val backStack: NavBackStack<NavKey>,
    private val snapshotStore: NavigationRouteSnapshotStore,
    initialSnapshotCleanupPending: Boolean,
    private val onDialogResult: (DialogResultDelivery) -> Unit,
    private val confirmDeleteDismissalLock: () -> ConfirmDeleteDismissalLock,
    private val onNavigationStarted: () -> Unit,
    private val onIncompleteOnboardingObservationRequested: () -> Unit,
    private val onboardingCompletionRetryPolicy: TodayAdmissionRetryPolicy = DefaultTodayAdmissionRetryPolicy,
) {
    private var nextResultId = 0L
    private var canonicalRoot: AppDestination = AppDestination.LaunchGate
    private var initialSnapshotCleanupPending = initialSnapshotCleanupPending
    private val navigationMutationMutex = NavigationMutationMutex()
    private val snapshotPersistenceMutex = Mutex()
    private val deferredExternalNavigation = DeferredExternalNavigationCoordinator(
        runNavigationAtomically = navigationMutationMutex::runAtomically,
    )
    private val coldLaunchGateAdmission = ColdLaunchGateAdmission()
    private val onboardingNavigationEffects = OnboardingNavigationEffectHandler(
        isOriginCurrent = { backStack.lastOrNull() == it },
        onBack = ::onBackLocked,
        onSetupCompleted = ::transitionCompletedOnboardingToToday,
    )

    suspend fun handleListEffect(effect: com.denis.habitlab.shared.presentation.experimentlist.NavigationEffect) {
        navigationMutationMutex.runAtomically {
            if (backStack.lastOrNull() != AppDestination.Today) return@runAtomically
            when (effect) {
                is com.denis.habitlab.shared.presentation.experimentlist.NavigationEffect.OpenDetails -> openDetailsLocked(effect.experimentId)
                com.denis.habitlab.shared.presentation.experimentlist.NavigationEffect.OpenCreateEditor -> addLocked(AppDestination.ExperimentEditor(null))
                com.denis.habitlab.shared.presentation.experimentlist.NavigationEffect.OpenSettings -> addLocked(AppDestination.Settings)
            }
        }
    }

    suspend fun handleDetailsEffect(
        origin: AppDestination.Experiment,
        effect: com.denis.habitlab.shared.presentation.experimentdetails.NavigationEffect,
    ) {
        navigationMutationMutex.runAtomically {
            if (effect == com.denis.habitlab.shared.presentation.experimentdetails.NavigationEffect.PopToRoot) {
                // A Room delete can make the underlying observer report Missing before its dialog's
                // confirmed effect is delivered. Preserve the pop-then-result dialog invariant.
                if (
                    backStack.lastOrNull() is AppDestination.ConfirmDelete &&
                    backStack.getOrNull(backStack.lastIndex - 1) == origin
                ) return@runAtomically
                if (origin in backStack) popToRootLocked()
                return@runAtomically
            }
            if (backStack.lastOrNull() != origin) return@runAtomically
            when (effect) {
                com.denis.habitlab.shared.presentation.experimentdetails.NavigationEffect.Back -> onBackLocked()
                is com.denis.habitlab.shared.presentation.experimentdetails.NavigationEffect.OpenEditor -> addLocked(AppDestination.ExperimentEditor(effect.experimentId))
                is com.denis.habitlab.shared.presentation.experimentdetails.NavigationEffect.OpenDailyCheckIn ->
                    addLocked(AppDestination.DailyCheckIn(effect.experimentId, CheckInRouteDate.from(effect.localDate)))
                is com.denis.habitlab.shared.presentation.experimentdetails.NavigationEffect.OpenConfirmDelete ->
                    addLocked(AppDestination.ConfirmDelete(effect.experimentId))
                com.denis.habitlab.shared.presentation.experimentdetails.NavigationEffect.PopToRoot -> Unit
            }
        }
    }

    suspend fun handleEditorEffect(
        origin: AppDestination.ExperimentEditor,
        effect: com.denis.habitlab.shared.presentation.experimenteditor.NavigationEffect,
    ) {
        navigationMutationMutex.runAtomically {
            if (effect == com.denis.habitlab.shared.presentation.experimenteditor.NavigationEffect.PopToRoot) {
                if (origin in backStack) popToRootLocked()
                return@runAtomically
            }
            if (backStack.lastOrNull() != origin) return@runAtomically
            when (effect) {
                com.denis.habitlab.shared.presentation.experimenteditor.NavigationEffect.Back -> onBackLocked()
                is com.denis.habitlab.shared.presentation.experimenteditor.NavigationEffect.OpenMetricPicker ->
                    addLocked(AppDestination.MetricPicker(effect.experimentId))
                is com.denis.habitlab.shared.presentation.experimenteditor.NavigationEffect.SaveComplete -> completeEditorLocked(origin, effect.experimentId)
                com.denis.habitlab.shared.presentation.experimenteditor.NavigationEffect.PopToRoot -> Unit
            }
        }
    }

    suspend fun handleCheckInEffect(
        origin: AppDestination.DailyCheckIn,
        effect: com.denis.habitlab.shared.presentation.dailycheckin.NavigationEffect,
    ) {
        navigationMutationMutex.runAtomically {
            if (effect == com.denis.habitlab.shared.presentation.dailycheckin.NavigationEffect.PopToRoot) {
                if (origin in backStack) popToRootLocked()
            } else if (backStack.lastOrNull() == origin && effect == com.denis.habitlab.shared.presentation.dailycheckin.NavigationEffect.Back) {
                onBackLocked()
            }
        }
    }

    suspend fun handleSettingsEffect(effect: com.denis.habitlab.shared.presentation.settings.NavigationEffect) {
        navigationMutationMutex.runAtomically {
            if (backStack.lastOrNull() == AppDestination.Settings && effect == com.denis.habitlab.shared.presentation.settings.NavigationEffect.Back) {
                onBackLocked()
            }
        }
    }

    suspend fun handleOnboardingEffect(
        origin: AppDestination,
        effect: com.denis.habitlab.shared.presentation.onboardingcheckpoint.NavigationEffect,
    ) {
        if (effect == com.denis.habitlab.shared.presentation.onboardingcheckpoint.NavigationEffect.Back) {
            navigationMutationMutex.runAtomically { onboardingNavigationEffects.handle(origin, effect) }
        } else {
            // Durable completion enters command → mutation through the deferred coordinator.
            onboardingNavigationEffects.handle(origin, effect)
        }
    }

    suspend fun handleLaunchGateEffect(
        decision: LaunchGateDecision,
        restoredCandidate: List<AppDestination>?,
    ): Boolean {
        if (!coldLaunchGateAdmission.mayResolve(backStack.lastOrNull() == AppDestination.LaunchGate)) return true
        val resolvedRoutes = when (decision) {
            LaunchGateDecision.Welcome -> listOf(AppDestination.Welcome)
            is LaunchGateDecision.ResumeOnboarding -> onboardingRoutes(decision.checkpoint)
            LaunchGateDecision.Today -> restoredCandidate
                ?.takeIf { NavigationRouteSnapshotCodec.isValidCompleteRoute(it) && it.firstOrNull() == AppDestination.Today }
                ?: listOf(AppDestination.Today)
        }
        return coldLaunchGateAdmission.resolve(
            prepareResolution = {
                if (decision == LaunchGateDecision.Today) {
                    var stagedTodayRoutes = resolvedRoutes
                    deferredExternalNavigation.admitToday(
                        prepareToday = {
                            // Keep the existing LaunchGate entry and ViewModel alive until the
                            // complete staged admission succeeds. That ViewModel owns Failed/Retry.
                            persistRoutes(stagedTodayRoutes, durable = true)
                        },
                        replayDeferredEvent = { event ->
                            stagedTodayRoutes = stageExternalNavigationRoute(event, durable = true)
                        },
                        commitToday = {
                            publishPersistedRoutesLocked(
                                routes = stagedTodayRoutes,
                                canonicalRootAfterPublish = AppDestination.Today,
                            )
                        },
                    )
                } else {
                    navigationMutationMutex.runAtomically {
                        publishRoutesLocked(
                            routes = resolvedRoutes,
                            canonicalRootAfterPublish = resolvedRoutes.first(),
                            durable = true,
                        )
                    }
                    onIncompleteOnboardingObservationRequested()
                }
            },
            restoreLaunchGate = {},
        )
    }

    /**
     * An invalid/failed durable read never resolves the gate, so it cannot reach [persist]. Keep
     * obsolete snapshot cleanup in this same navigator owner to share the initial-clear invariant
     * with a later successful retry instead of launching an independent composition effect.
     */
    suspend fun clearObsoleteSnapshotAfterBlockedLaunch(): Boolean = try {
        navigationMutationMutex.runAtomically { clearSnapshotLocked(durable = true) }
        true
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        false
    }

    suspend fun handleMetricPickerEffect(
        origin: AppDestination.MetricPicker,
        effect: com.denis.habitlab.shared.presentation.metricpicker.NavigationEffect,
    ) {
        navigationMutationMutex.runAtomically {
            if (backStack.lastOrNull() == origin && effect is com.denis.habitlab.shared.presentation.metricpicker.NavigationEffect.Resolve) {
                resolveMetricLocked(origin, effect.result)
            }
        }
    }

    suspend fun handleConfirmDeleteEffect(
        origin: AppDestination.ConfirmDelete,
        effect: com.denis.habitlab.shared.presentation.confirmdelete.NavigationEffect,
    ) {
        navigationMutationMutex.runAtomically {
            if (backStack.lastOrNull() == origin && effect is com.denis.habitlab.shared.presentation.confirmdelete.NavigationEffect.Resolve) {
                resolveDeleteLocked(origin, effect.result)
            }
        }
    }

    suspend fun onBack() {
        navigationMutationMutex.runAtomically { onBackLocked() }
    }

    private suspend fun onBackLocked() {
        when (val top = backStack.lastOrNull()) {
            is AppDestination.MetricPicker -> resolveMetricLocked(top, MetricPickerResult.Cancelled(top.experimentId))
            is AppDestination.ConfirmDelete -> {
                when (ConfirmDeleteDismissalPolicy.decide(confirmDeleteDismissalLock())) {
                    ConfirmDeleteDismissalDecision.Ignore -> Unit
                    ConfirmDeleteDismissalDecision.ResolveCancelled -> {
                        resolveDeleteLocked(top, DeleteDialogResult.Cancelled(top.experimentId))
                    }
                }
            }
            null -> Unit
            else -> {
                if (backStack.size <= 1) return
                val routes = currentRoutesLocked() ?: return
                publishRoutesLocked(routes.dropLast(1))
            }
        }
    }

    /** Native delivery stays in FIFO custody until the Today admission handoff has completed. */
    suspend fun deferExternalNavigation(event: ExternalNavigationEvent) {
        deferredExternalNavigation.deferOrProcess(event, ::handleExternalNavigationAfterTodayAdmission)
    }

    /** SETUP observed durable completion; serialize Today persistence before replaying deferred links. */
    private suspend fun transitionCompletedOnboardingToToday() {
        var stagedTodayRoutes = listOf<AppDestination>(AppDestination.Today)
        retryTodayAdmission(onboardingCompletionRetryPolicy) {
            deferredExternalNavigation.admitTodayOrRollback(
                prepareToday = {
                    stagedTodayRoutes = listOf(AppDestination.Today)
                    persistRoutes(stagedTodayRoutes, durable = true)
                },
                rollback = {
                    stagedTodayRoutes = listOf(AppDestination.Today)
                },
                replayDeferredEvent = { event ->
                    stagedTodayRoutes = stageExternalNavigationRoute(event, durable = true)
                },
                commitToday = {
                    publishPersistedRoutesLocked(
                        routes = stagedTodayRoutes,
                        canonicalRootAfterPublish = AppDestination.Today,
                    )
                },
            )
        }
    }

    private suspend fun handleExternalNavigationAfterTodayAdmission(event: ExternalNavigationEvent) {
        navigationMutationMutex.runAtomically {
            var routes: List<AppDestination>? = null
            persistThenPublish(
                persistStagedRoute = { routes = stageExternalNavigationRoute(event, durable = false) },
                publishVisibleRoute = { publishPersistedRoutesLocked(requireNotNull(routes)) },
            )
        }
    }

    /** Persists one external URL's complete Today route without mutating the active Nav3 stack. */
    private suspend fun stageExternalNavigationRoute(
        event: ExternalNavigationEvent,
        durable: Boolean,
    ): List<AppDestination> {
        val routes = listOfNotNull(AppDestination.Today, HabitLabDeepLink.parse(event.rawUrl))
        persistRoutes(routes, durable)
        return routes
    }

    private suspend fun openDetailsLocked(experimentId: ExperimentId) {
        if (ExperimentId.fromInternalValue(experimentId.value) == null) {
            popToRootLocked()
        } else {
            addLocked(AppDestination.Experiment(experimentId))
        }
    }

    private suspend fun addLocked(destination: AppDestination) {
        val routes = currentRoutesLocked() ?: return
        publishRoutesLocked(routes + destination)
    }

    private suspend fun completeEditorLocked(origin: AppDestination.ExperimentEditor, experimentId: ExperimentId) {
        if (origin.experimentId != null && origin.experimentId != experimentId) {
            popToRootLocked()
            return
        }
        val routes = currentRoutesLocked() ?: return
        val completedRoutes = routes.dropLast(1).let { previous ->
            if (origin.experimentId == null) previous + AppDestination.Experiment(experimentId) else previous
        }
        publishRoutesLocked(completedRoutes)
    }

    private suspend fun resolveMetricLocked(dialog: AppDestination.MetricPicker, result: MetricPickerResult) {
        val caller = backStack.getOrNull(backStack.lastIndex - 1) as? AppDestination.ExperimentEditor
        if (caller == null || caller.experimentId != dialog.experimentId || result.experimentId != dialog.experimentId) {
            popToRootLocked()
            return
        }
        resolveLocked(caller, DialogResult.Metric(result))
    }

    private suspend fun resolveDeleteLocked(dialog: AppDestination.ConfirmDelete, result: DeleteDialogResult) {
        val caller = backStack.getOrNull(backStack.lastIndex - 1) as? AppDestination.Experiment
        if (caller?.experimentId != dialog.experimentId || result.experimentId != dialog.experimentId) {
            popToRootLocked()
            return
        }
        resolveLocked(caller, DialogResult.Delete(result))
    }

    private suspend fun resolveLocked(caller: AppDestination, result: DialogResult) {
        val routes = currentRoutesLocked() ?: return
        publishRoutesLocked(routes.dropLast(1)) {
            nextResultId += 1
            onDialogResult(DialogResultDelivery(nextResultId, caller, result))
        }
    }

    private suspend fun popToRootLocked() {
        if (currentRoutesLocked() == null) return
        publishRoutesLocked(listOf(canonicalRoot))
    }

    /** Must be called with [navigationMutationMutex] held. */
    private suspend fun publishRoutesLocked(
        routes: List<AppDestination>,
        canonicalRootAfterPublish: AppDestination? = null,
        durable: Boolean = false,
        afterPublish: () -> Unit = {},
    ) {
        persistThenPublish(
            persistStagedRoute = { persistRoutes(routes, durable) },
            publishVisibleRoute = { publishPersistedRoutesLocked(routes, canonicalRootAfterPublish, afterPublish) },
        )
    }

    /** Must be called with [navigationMutationMutex] held after [persistRoutes] succeeds. */
    private fun publishPersistedRoutesLocked(
        routes: List<AppDestination>,
        canonicalRootAfterPublish: AppDestination? = null,
        afterPublish: () -> Unit = {},
    ) {
        onNavigationStarted()
        if (canonicalRootAfterPublish != null) canonicalRoot = canonicalRootAfterPublish
        replaceWith(routes)
        afterPublish()
    }

    private fun replaceWith(routes: List<AppDestination>) {
        backStack.clear()
        backStack.addAll(routes)
    }

    private fun onboardingRoutes(checkpoint: OnboardingStep): List<AppDestination> {
        if (checkpoint == OnboardingStep.WELCOME) return listOf(AppDestination.Welcome)
        val checkpoints = OnboardingStep.entries
            .dropWhile { it != OnboardingStep.OUTCOME }
            .takeWhile { it != checkpoint }
            .plus(checkpoint)
        return listOf(AppDestination.Welcome) + checkpoints.map(AppDestination::OnboardingCheckpoint)
    }

    /** Must be called with [navigationMutationMutex] held. */
    private suspend fun currentRoutesLocked(): List<AppDestination>? = appDestinationRoutesOrClear(backStack) {
        clearSnapshotLocked(durable = false)
    }

    /** Must be called with [navigationMutationMutex] held. */
    private suspend fun clearSnapshotLocked(durable: Boolean) = withContext(NonCancellable) {
        snapshotPersistenceMutex.withLock {
            persistInitialSnapshotCleanupIfPending(durable)
            NavigationRouteSnapshotCodec.clear(snapshotStore, durable)
        }
    }

    /** Serializes an explicit staged route before its corresponding Nav3 mutation becomes visible. */
    private suspend fun persistRoutes(routes: List<AppDestination>, durable: Boolean) = withContext(NonCancellable) {
        snapshotPersistenceMutex.withLock {
            persistInitialSnapshotCleanupIfPending(durable)
            NavigationRouteSnapshotCodec.persist(snapshotStore, routes, durable)
        }
    }

    private suspend fun persistInitialSnapshotCleanupIfPending(durable: Boolean) {
        if (initialSnapshotCleanupPending) {
            // The obsolete clear and first v3 write are one navigator-owned operation. This
            // prevents a late composition effect from erasing the newly persisted route.
            NavigationRouteSnapshotCodec.clear(snapshotStore, durable)
            initialSnapshotCleanupPending = false
        }
    }
}
