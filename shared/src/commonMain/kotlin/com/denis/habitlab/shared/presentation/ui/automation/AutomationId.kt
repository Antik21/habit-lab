package com.denis.habitlab.shared.presentation.ui.automation

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Closed, stable identifiers for UI automation. New values can only be declared in this contract,
 * which prevents product code from passing locale-, user-, or runtime-derived selectors.
 */
enum class AutomationId(
    val value: String,
) {
    GalleryScreenRoot("habitlab.gallery.screen.root"),
    GalleryToolbarBack("habitlab.gallery.toolbar.back"),
    GalleryPrimaryAction("habitlab.gallery.action.primary"),
    GallerySecondaryAction("habitlab.gallery.action.secondary"),
    GalleryTextField("habitlab.gallery.field.habit-name"),
    GalleryFirstRow("habitlab.gallery.row.daily-movement"),
    GallerySecondRow("habitlab.gallery.row.sleep-routine"),
    GalleryDialogRoot("habitlab.gallery.dialog.root"),
    GalleryDialogConfirm("habitlab.gallery.dialog.confirm"),
    GalleryDialogDismiss("habitlab.gallery.dialog.dismiss"),
    GalleryLoadingState("habitlab.gallery.state.loading"),
    GalleryEmptyState("habitlab.gallery.state.empty"),
    GalleryErrorState("habitlab.gallery.state.error"),
    NavigationExperimentScreenRoot("habitlab.navigation.experiment.screen.root"),
    NavigationExperimentToolbarBack("habitlab.navigation.experiment.toolbar.back"),
    NavigationExperimentOpenDialog("habitlab.navigation.experiment.action.open-dialog"),
    NavigationExperimentStartFlow("habitlab.navigation.experiment.action.start-flow"),
    NavigationExperimentOpenSettings("habitlab.navigation.experiment.action.open-settings"),
    NavigationExperimentLoadingState("habitlab.navigation.experiment.state.loading"),
    NavigationExperimentErrorState("habitlab.navigation.experiment.state.error"),
    NavigationFlowStepOneScreenRoot("habitlab.navigation.flow.step-one.screen.root"),
    NavigationFlowStepOneToolbarBack("habitlab.navigation.flow.step-one.toolbar.back"),
    NavigationFlowStepOneNext("habitlab.navigation.flow.step-one.action.next"),
    NavigationFlowStepTwoScreenRoot("habitlab.navigation.flow.step-two.screen.root"),
    NavigationFlowStepTwoToolbarBack("habitlab.navigation.flow.step-two.toolbar.back"),
    NavigationFlowStepTwoFinish("habitlab.navigation.flow.step-two.action.finish"),
    NavigationDialogScreenRoot("habitlab.navigation.dialog.screen.root"),
    NavigationDialogConfirm("habitlab.navigation.dialog.action.confirm"),
    NavigationDialogCancel("habitlab.navigation.dialog.action.cancel"),
    NavigationDialogResultConfirmed("habitlab.navigation.dialog.result.confirmed"),
    NavigationDialogResultCancelled("habitlab.navigation.dialog.result.cancelled"),
    ;

    internal companion object {
        fun validateContract() {
            val values = entries.map(AutomationId::value)
            require(values.all { value -> SUPPORTED_NAMESPACES.any(value::startsWith) }) {
                "Automation IDs must use one of the declared Habit Lab namespaces."
            }
            require(values.toSet().size == values.size) {
                "Automation IDs must be unique."
            }
        }

        private val SUPPORTED_NAMESPACES = listOf(
            "habitlab.gallery.",
            "habitlab.navigation.",
        )
    }
}

/** Fixed selectors exposed by the shared production Navigation 3 shell. */
object NavigationSpikeAutomationIds {
    val experimentScreenRoot = AutomationId.NavigationExperimentScreenRoot
    val experimentToolbarBack = AutomationId.NavigationExperimentToolbarBack
    val experimentOpenDialog = AutomationId.NavigationExperimentOpenDialog
    val experimentStartFlow = AutomationId.NavigationExperimentStartFlow
    val experimentOpenSettings = AutomationId.NavigationExperimentOpenSettings
    val experimentLoadingState = AutomationId.NavigationExperimentLoadingState
    val experimentErrorState = AutomationId.NavigationExperimentErrorState
    val flowStepOneScreenRoot = AutomationId.NavigationFlowStepOneScreenRoot
    val flowStepOneToolbarBack = AutomationId.NavigationFlowStepOneToolbarBack
    val flowStepOneNext = AutomationId.NavigationFlowStepOneNext
    val flowStepTwoScreenRoot = AutomationId.NavigationFlowStepTwoScreenRoot
    val flowStepTwoToolbarBack = AutomationId.NavigationFlowStepTwoToolbarBack
    val flowStepTwoFinish = AutomationId.NavigationFlowStepTwoFinish
    val dialogScreenRoot = AutomationId.NavigationDialogScreenRoot
    val dialogConfirm = AutomationId.NavigationDialogConfirm
    val dialogCancel = AutomationId.NavigationDialogCancel
    val dialogResultConfirmed = AutomationId.NavigationDialogResultConfirmed
    val dialogResultCancelled = AutomationId.NavigationDialogResultCancelled
}

/**
 * The fixed, locale-independent selectors exposed by the component-gallery bootstrap screen.
 */
object ComponentGalleryAutomationIds {
    val screenRoot = AutomationId.GalleryScreenRoot
    val toolbarBack = AutomationId.GalleryToolbarBack
    val primaryAction = AutomationId.GalleryPrimaryAction
    val secondaryAction = AutomationId.GallerySecondaryAction
    val textField = AutomationId.GalleryTextField
    val firstRow = AutomationId.GalleryFirstRow
    val secondRow = AutomationId.GallerySecondRow
    val dialogRoot = AutomationId.GalleryDialogRoot
    val dialogConfirm = AutomationId.GalleryDialogConfirm
    val dialogDismiss = AutomationId.GalleryDialogDismiss
    val loadingState = AutomationId.GalleryLoadingState
    val emptyState = AutomationId.GalleryEmptyState
    val errorState = AutomationId.GalleryErrorState

    val all: List<AutomationId> = AutomationId.entries

    init {
        AutomationId.validateContract()
    }
}

/** Applies an identifier directly to the component's target semantics node. */
fun Modifier.autodevId(id: AutomationId): Modifier = enableAutodevResourceIds().testTag(id.value)
