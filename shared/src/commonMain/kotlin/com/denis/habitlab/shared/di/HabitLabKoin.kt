package com.denis.habitlab.shared.di

import com.denis.habitlab.shared.core.platform.PlatformDescriptor
import com.denis.habitlab.shared.data.local.DebugExperimentDatabaseControl
import com.denis.habitlab.shared.data.local.DebugDatabaseSeedResult
import com.denis.habitlab.shared.data.local.HabitLabDatabase
import com.denis.habitlab.shared.data.local.RoomExperimentLocalDataSource
import com.denis.habitlab.shared.data.observer.RoomExperimentObservers
import com.denis.habitlab.shared.data.repository.AppMetadataRepositoryImpl
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
import com.denis.habitlab.shared.domain.observer.DailyCheckInObserver
import com.denis.habitlab.shared.domain.observer.ExperimentListObserver
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObserver
import com.denis.habitlab.shared.domain.repository.AppMetadataRepository
import com.denis.habitlab.shared.domain.repository.ExperimentRepository
import com.denis.habitlab.shared.presentation.AppPresenter
import com.denis.habitlab.shared.presentation.navigation.ConfirmationDialogEntryViewModel
import com.denis.habitlab.shared.presentation.navigation.ExperimentEntryViewModel
import com.denis.habitlab.shared.presentation.navigation.FlowEntryViewModel
import com.denis.habitlab.shared.presentation.navigation.GalleryEntryViewModel
import kotlinx.coroutines.runBlocking
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.module

/** A host-owned runtime keeps the debug reset capability explicit instead of using a service locator. */
class HabitLabRuntime internal constructor(
    val presenter: AppPresenter,
    val debugDatabaseControl: DebugExperimentDatabaseControl?,
)

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
    )
}

/** Compatibility factory for callers that only need the presenter. */
fun initHabitLabKoin(
    platformDescriptor: PlatformDescriptor,
    database: HabitLabDatabase,
    isDebugBuild: Boolean,
): AppPresenter = initHabitLabRuntime(
    platformDescriptor = platformDescriptor,
    database = database,
    isDebugBuild = isDebugBuild,
).presenter

private fun habitLabModule(
    platformDescriptor: PlatformDescriptor,
    database: HabitLabDatabase,
    isDebugBuild: Boolean,
) = module {
    single<PlatformDescriptor> { platformDescriptor }
    single { database }
    single { RoomExperimentLocalDataSource(database = get()) }
    single<ExperimentRepository> { RoomExperimentRepository(localDataSource = get()) }
    single { RoomExperimentObservers(localDataSource = get()) }
    single<ExperimentProjectionObserver> { get<RoomExperimentObservers>() }
    single<ExperimentListObserver> { get<RoomExperimentObservers>() }
    single<DailyCheckInObserver> { get<RoomExperimentObservers>() }
    single<ExperimentIdSource> { RandomDraftExperimentIdSource() }
    single<RecordedAtSource> { SystemRecordedAtSource() }
    single { CreateExperimentDraft(repository = get(), idSource = get(), recordedAtSource = get()) }
    single { EditExperimentDraft(repository = get(), recordedAtSource = get()) }
    single { RecordDailyCheckIn(repository = get(), recordedAtSource = get()) }
    if (isDebugBuild) {
        single(createdAtStart = true) {
            DebugExperimentDatabaseControl(localDataSource = get()).also { debugControl ->
                // The debug seed is complete before a ViewModel can subscribe and see a false missing state.
                check(runBlocking { debugControl.seedIfEmpty() } !is DebugDatabaseSeedResult.Failed) {
                    "Debug database seed failed"
                }
            }
        }
    }
    single<AppMetadataRepository> {
        AppMetadataRepositoryImpl(
            dataSource = PlatformAppMetadataDataSource(platformDescriptor = get()),
        )
    }
    single { GetAppMetadata(repository = get()) }
    factory { AppPresenter(getAppMetadata = get()) }
    factory { GalleryEntryViewModel(experimentListObserver = get()) }
    factory { parameters ->
        ExperimentEntryViewModel(
            experimentId = parameters.get(),
            projectionObserver = get(),
        )
    }
    factory { parameters -> FlowEntryViewModel(flowId = parameters.get()) }
    factory { parameters -> ConfirmationDialogEntryViewModel(experimentId = parameters.get()) }
}
