package com.denis.habitlab.shared.di

import com.denis.habitlab.shared.core.platform.PlatformDescriptor
import com.denis.habitlab.shared.data.local.DatabaseReadiness
import com.denis.habitlab.shared.data.local.DatabaseReadinessState
import com.denis.habitlab.shared.data.local.DebugDatabaseBootstrap
import com.denis.habitlab.shared.data.local.DebugExperimentDatabaseControl
import com.denis.habitlab.shared.data.local.HabitLabDatabase
import com.denis.habitlab.shared.data.local.RoomExperimentLocalDataSource
import com.denis.habitlab.shared.data.observer.RoomExperimentObservers
import com.denis.habitlab.shared.data.repository.AppMetadataRepositoryImpl
import com.denis.habitlab.shared.data.repository.RuntimeAppPreferenceRepository
import com.denis.habitlab.shared.data.repository.RecordedAtCurrentLocalDateSource
import com.denis.habitlab.shared.data.repository.PlatformAppMetadataDataSource
import com.denis.habitlab.shared.data.repository.RandomDraftExperimentIdSource
import com.denis.habitlab.shared.data.repository.RoomExperimentRepository
import com.denis.habitlab.shared.data.repository.SystemRecordedAtSource
import com.denis.habitlab.shared.domain.interactor.RecordedAtSource
import com.denis.habitlab.shared.domain.interactor.CreateExperimentDraft
import com.denis.habitlab.shared.domain.interactor.EditExperimentDraft
import com.denis.habitlab.shared.domain.interactor.ExperimentIdSource
import com.denis.habitlab.shared.domain.interactor.GetAppMetadata
import com.denis.habitlab.shared.domain.interactor.RecordDailyCheckIn
import com.denis.habitlab.shared.domain.interactor.DeleteExperiment
import com.denis.habitlab.shared.domain.interactor.CurrentLocalDateSource
import com.denis.habitlab.shared.domain.interactor.GetCurrentLocalDate
import com.denis.habitlab.shared.domain.interactor.ObserveThemePreference
import com.denis.habitlab.shared.domain.interactor.SetThemePreference
import com.denis.habitlab.shared.domain.observer.DailyCheckInObserver
import com.denis.habitlab.shared.domain.observer.ExperimentListObserver
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObserver
import com.denis.habitlab.shared.domain.repository.AppMetadataRepository
import com.denis.habitlab.shared.domain.repository.ExperimentRepository
import com.denis.habitlab.shared.domain.repository.AppPreferenceRepository
import com.denis.habitlab.shared.presentation.AppPresenter
import com.denis.habitlab.shared.presentation.gallery.ComponentGalleryUiMapper
import com.denis.habitlab.shared.presentation.gallery.ComponentGalleryViewModel
import com.denis.habitlab.shared.presentation.experimentlist.ExperimentListUiMapper
import com.denis.habitlab.shared.presentation.experimentlist.ExperimentListViewModel
import com.denis.habitlab.shared.presentation.experimentdetails.ExperimentDetailsUiMapper
import com.denis.habitlab.shared.presentation.experimentdetails.ExperimentDetailsViewModel
import com.denis.habitlab.shared.presentation.experimenteditor.ExperimentEditorUiMapper
import com.denis.habitlab.shared.presentation.experimenteditor.ExperimentEditorViewModel
import com.denis.habitlab.shared.presentation.dailycheckin.DailyCheckInUiMapper
import com.denis.habitlab.shared.presentation.dailycheckin.DailyCheckInViewModel
import com.denis.habitlab.shared.presentation.settings.SettingsUiMapper
import com.denis.habitlab.shared.presentation.settings.SettingsViewModel
import com.denis.habitlab.shared.presentation.metricpicker.MetricPickerViewModel
import com.denis.habitlab.shared.presentation.confirmdelete.ConfirmDeleteViewModel
import com.denis.habitlab.shared.presentation.navigation.ExperimentEditorEntryArguments
import com.denis.habitlab.shared.presentation.navigation.MetricPickerEntryArguments
import com.denis.habitlab.shared.presentation.navigation.confirmation.NavigationConfirmationDialogViewModel
import com.denis.habitlab.shared.presentation.navigation.experiment.NavigationExperimentViewModel
import com.denis.habitlab.shared.presentation.navigation.experiment.NavigationExperimentUiMapper
import com.denis.habitlab.shared.presentation.navigation.flow.stepone.NavigationFlowStepOneViewModel
import com.denis.habitlab.shared.presentation.navigation.flow.steptwo.NavigationFlowStepTwoViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.module

/** A host-owned runtime keeps the debug reset capability explicit instead of using a service locator. */
class HabitLabRuntime internal constructor(
    val presenter: AppPresenter,
    val debugDatabaseControl: DebugExperimentDatabaseControl?,
    private val debugDatabaseBootstrap: DebugDatabaseBootstrap?,
) {
    private val runtimeJob = SupervisorJob()
    private val initializationScope = CoroutineScope(runtimeJob + Dispatchers.Default)
    private var initializationJob: Job? = null

    /**
     * Starts debug-only database initialization without blocking the host's startup thread.
     * Observers remain in their Loading state until the gate becomes ready or reports failure.
     */
    fun initialize() {
        if (initializationJob != null) return
        initializationJob = debugDatabaseBootstrap?.let { bootstrap ->
            initializationScope.launch { bootstrap.initialize() }
        }
    }

    /** Cancels the host-owned bootstrap scope when the runtime is permanently discarded. */
    fun close() {
        runtimeJob.cancel()
    }
}

fun initHabitLabRuntime(
    platformDescriptor: PlatformDescriptor,
    database: HabitLabDatabase,
    isDebugBuild: Boolean,
    applicationDeclaration: (KoinApplication.() -> Unit)? = null,
): HabitLabRuntime {
    val koin = startKoin {
        applicationDeclaration?.invoke(this)
        modules(habitLabModule(platformDescriptor, database, isDebugBuild))
    }.koin
    return HabitLabRuntime(
        presenter = koin.get(),
        debugDatabaseControl = if (isDebugBuild) koin.get() else null,
        debugDatabaseBootstrap = if (isDebugBuild) koin.get() else null,
    )
}

/** Compatibility factory for callers that only need the presenter. */
fun initHabitLabKoin(
    platformDescriptor: PlatformDescriptor,
    database: HabitLabDatabase,
    isDebugBuild: Boolean,
): AppPresenter {
    val runtime = initHabitLabRuntime(
        platformDescriptor = platformDescriptor,
        database = database,
        isDebugBuild = isDebugBuild,
    )
    runtime.initialize()
    return runtime.presenter
}

private fun habitLabModule(
    platformDescriptor: PlatformDescriptor,
    database: HabitLabDatabase,
    isDebugBuild: Boolean,
) = module {
    single<PlatformDescriptor> { platformDescriptor }
    single { database }
    single {
        DatabaseReadiness(
            if (isDebugBuild) DatabaseReadinessState.Initializing else DatabaseReadinessState.Ready,
        )
    }
    single { RoomExperimentLocalDataSource(database = get()) }
    single<ExperimentRepository> { RoomExperimentRepository(localDataSource = get()) }
    single { RoomExperimentObservers(localDataSource = get(), databaseReadiness = get()) }
    single<ExperimentProjectionObserver> { get<RoomExperimentObservers>() }
    single<ExperimentListObserver> { get<RoomExperimentObservers>() }
    single<DailyCheckInObserver> { get<RoomExperimentObservers>() }
    single<ExperimentIdSource> { RandomDraftExperimentIdSource() }
    single<RecordedAtSource> { SystemRecordedAtSource() }
    single<CurrentLocalDateSource> { RecordedAtCurrentLocalDateSource(recordedAtSource = get()) }
    single { CreateExperimentDraft(repository = get(), idSource = get(), recordedAtSource = get()) }
    single { EditExperimentDraft(repository = get(), recordedAtSource = get()) }
    single { RecordDailyCheckIn(repository = get(), recordedAtSource = get()) }
    single { DeleteExperiment(repository = get()) }
    single { GetCurrentLocalDate(source = get()) }
    single<AppPreferenceRepository> { RuntimeAppPreferenceRepository() }
    single { ObserveThemePreference(repository = get()) }
    single { SetThemePreference(repository = get()) }
    if (isDebugBuild) {
        single {
            val databaseReadiness: DatabaseReadiness = get()
            DebugExperimentDatabaseControl(
                localDataSource = get(),
                onSuccessfulReset = databaseReadiness::markReady,
            )
        }
        single { DebugDatabaseBootstrap(debugDatabaseControl = get(), databaseReadiness = get()) }
    }
    single<AppMetadataRepository> {
        AppMetadataRepositoryImpl(
            dataSource = PlatformAppMetadataDataSource(platformDescriptor = get()),
        )
    }
    single { GetAppMetadata(repository = get()) }
    factory { AppPresenter(getAppMetadata = get(), observeThemePreference = get()) }
    factory { ComponentGalleryUiMapper() }
    factory {
        ComponentGalleryViewModel(
            experimentListObserver = get(),
            uiMapper = get(),
        )
    }
    factory { ExperimentListUiMapper() }
    factory { ExperimentListViewModel(experimentListObserver = get(), uiMapper = get()) }
    factory { ExperimentDetailsUiMapper() }
    factory { parameters ->
        ExperimentDetailsViewModel(
            experimentId = parameters.get(),
            projectionObserver = get(),
            getCurrentLocalDate = get(),
            uiMapper = get(),
        )
    }
    factory { ExperimentEditorUiMapper() }
    factory { parameters ->
        ExperimentEditorViewModel(
            experimentId = parameters.get<ExperimentEditorEntryArguments>().experimentId,
            projectionObserver = get(),
            createExperimentDraft = get(),
            editExperimentDraft = get(),
            uiMapper = get(),
        )
    }
    factory { DailyCheckInUiMapper() }
    factory { parameters ->
        DailyCheckInViewModel(
            experimentId = parameters.get(),
            localDate = parameters.get(),
            dailyCheckInObserver = get(),
            recordDailyCheckIn = get(),
            uiMapper = get(),
        )
    }
    factory { SettingsUiMapper() }
    factory { SettingsViewModel(observeThemePreference = get(), setThemePreference = get(), uiMapper = get()) }
    factory { parameters ->
        MetricPickerViewModel(experimentId = parameters.get<MetricPickerEntryArguments>().experimentId)
    }
    factory { parameters -> ConfirmDeleteViewModel(experimentId = parameters.get(), deleteExperiment = get()) }
    factory { NavigationExperimentUiMapper() }
    factory { parameters ->
        NavigationExperimentViewModel(
            experimentId = parameters.get(),
            projectionObserver = get(),
            uiMapper = get(),
        )
    }
    factory { parameters -> NavigationFlowStepOneViewModel(flowId = parameters.get()) }
    factory { parameters -> NavigationFlowStepTwoViewModel(flowId = parameters.get()) }
    factory { parameters ->
        NavigationConfirmationDialogViewModel(experimentId = parameters.get())
    }
}
