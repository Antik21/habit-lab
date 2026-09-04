package com.denis.habitlab.shared.di

import com.denis.habitlab.shared.core.platform.PlatformDescriptor
import com.denis.habitlab.shared.data.observer.InMemoryExperimentProjectionObserver
import com.denis.habitlab.shared.data.repository.AppMetadataRepositoryImpl
import com.denis.habitlab.shared.data.repository.PlatformAppMetadataDataSource
import com.denis.habitlab.shared.domain.interactor.GetAppMetadata
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObserver
import com.denis.habitlab.shared.domain.repository.AppMetadataRepository
import com.denis.habitlab.shared.presentation.AppPresenter
import com.denis.habitlab.shared.presentation.navigation.ConfirmationDialogEntryViewModel
import com.denis.habitlab.shared.presentation.navigation.ExperimentEntryViewModel
import com.denis.habitlab.shared.presentation.navigation.FlowEntryViewModel
import com.denis.habitlab.shared.presentation.navigation.GalleryEntryViewModel
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.module

/** Starts the common graph and returns the root presenter for hosts without extra Koin setup. */
fun initHabitLabKoin(platformDescriptor: PlatformDescriptor): AppPresenter =
    startKoin {
        modules(habitLabModule(platformDescriptor))
    }.koin.get()

/** Lets an Android host configure Koin before loading shared wiring and resolving the root presenter. */
fun initHabitLabKoin(
    platformDescriptor: PlatformDescriptor,
    applicationDeclaration: KoinApplication.() -> Unit,
): AppPresenter =
    startKoin {
        applicationDeclaration()
        modules(habitLabModule(platformDescriptor))
    }.koin.get()

private fun habitLabModule(platformDescriptor: PlatformDescriptor) = module {
    single<PlatformDescriptor> { platformDescriptor }
    single<AppMetadataRepository> {
        AppMetadataRepositoryImpl(
            dataSource = PlatformAppMetadataDataSource(platformDescriptor = get()),
        )
    }
    single { GetAppMetadata(repository = get()) }
    single<ExperimentProjectionObserver> { InMemoryExperimentProjectionObserver() }
    factory { AppPresenter(getAppMetadata = get()) }
    factory { GalleryEntryViewModel() }
    factory { parameters ->
        ExperimentEntryViewModel(
            experimentId = parameters.get(),
            projectionObserver = get(),
        )
    }
    factory { parameters -> FlowEntryViewModel(flowId = parameters.get()) }
    factory { parameters -> ConfirmationDialogEntryViewModel(experimentId = parameters.get()) }
}
