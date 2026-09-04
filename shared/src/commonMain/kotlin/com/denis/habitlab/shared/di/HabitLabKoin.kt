package com.denis.habitlab.shared.di

import com.denis.habitlab.shared.core.platform.PlatformDescriptor
import com.denis.habitlab.shared.data.observer.InMemoryExperimentProjectionObserver
import com.denis.habitlab.shared.data.repository.AppMetadataRepositoryImpl
import com.denis.habitlab.shared.data.repository.PlatformAppMetadataDataSource
import com.denis.habitlab.shared.domain.interactor.GetAppMetadata
import com.denis.habitlab.shared.domain.observer.ExperimentProjectionObserver
import com.denis.habitlab.shared.domain.repository.AppMetadataRepository
import com.denis.habitlab.shared.presentation.AppPresenter
import com.denis.habitlab.shared.presentation.gallery.ComponentGalleryViewModel
import com.denis.habitlab.shared.presentation.navigation.confirmation.NavigationConfirmationDialogViewModel
import com.denis.habitlab.shared.presentation.navigation.experiment.NavigationExperimentViewModel
import com.denis.habitlab.shared.presentation.navigation.experiment.NavigationExperimentUiMapper
import com.denis.habitlab.shared.presentation.navigation.flow.stepone.NavigationFlowStepOneViewModel
import com.denis.habitlab.shared.presentation.navigation.flow.steptwo.NavigationFlowStepTwoViewModel
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
    factory { NavigationExperimentUiMapper() }
    factory { AppPresenter(getAppMetadata = get()) }
    factory { ComponentGalleryViewModel() }
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
