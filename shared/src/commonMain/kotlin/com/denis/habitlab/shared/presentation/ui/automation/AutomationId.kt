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
    ExperimentListScreenRoot("habitlab.experiment-list.screen.root"),
    ExperimentListOpenSettings("habitlab.experiment-list.action.open-settings"),
    ExperimentListCreate("habitlab.experiment-list.action.create"),
    ExperimentListLoading("habitlab.experiment-list.state.loading"),
    ExperimentListEmpty("habitlab.experiment-list.state.empty"),
    ExperimentListError("habitlab.experiment-list.state.error"),
    ExperimentListRow("habitlab.experiment-list.row"),
    ExperimentListDailyMovementRow("habitlab.experiment-list.row.daily-movement"),
    ExperimentListSleepRoutineRow("habitlab.experiment-list.row.sleep-routine"),
    ExperimentDetailsScreenRoot("habitlab.experiment-details.screen.root"),
    ExperimentDetailsBack("habitlab.experiment-details.action.back"),
    ExperimentDetailsEdit("habitlab.experiment-details.action.edit"),
    ExperimentDetailsCheckIn("habitlab.experiment-details.action.check-in"),
    ExperimentDetailsDelete("habitlab.experiment-details.action.delete"),
    ExperimentDetailsLoading("habitlab.experiment-details.state.loading"),
    ExperimentDetailsError("habitlab.experiment-details.state.error"),
    ExperimentEditorScreenRoot("habitlab.experiment-editor.screen.root"),
    ExperimentEditorBack("habitlab.experiment-editor.action.back"),
    ExperimentEditorName("habitlab.experiment-editor.field.name"),
    ExperimentEditorMetric("habitlab.experiment-editor.action.metric"),
    ExperimentEditorMetricUnset("habitlab.experiment-editor.state.metric.unset"),
    ExperimentEditorMetricDailyEnergy("habitlab.experiment-editor.state.metric.daily-energy"),
    ExperimentEditorMetricSleepQuality("habitlab.experiment-editor.state.metric.sleep-quality"),
    ExperimentEditorSave("habitlab.experiment-editor.action.save"),
    ExperimentEditorLoading("habitlab.experiment-editor.state.loading"),
    ExperimentEditorError("habitlab.experiment-editor.state.error"),
    ExperimentEditorValidation("habitlab.experiment-editor.state.validation"),
    DailyCheckInScreenRoot("habitlab.daily-check-in.screen.root"),
    DailyCheckInBack("habitlab.daily-check-in.action.back"),
    DailyCheckInPerformed("habitlab.daily-check-in.action.performed"),
    DailyCheckInSkipped("habitlab.daily-check-in.action.skipped"),
    DailyCheckInOutcomePerformed("habitlab.daily-check-in.state.outcome.performed"),
    DailyCheckInOutcomeSkipped("habitlab.daily-check-in.state.outcome.skipped"),
    DailyCheckInSave("habitlab.daily-check-in.action.save"),
    DailyCheckInLoading("habitlab.daily-check-in.state.loading"),
    DailyCheckInError("habitlab.daily-check-in.state.error"),
    SettingsScreenRoot("habitlab.settings.screen.root"),
    SettingsBack("habitlab.settings.action.back"),
    SettingsThemeSystem("habitlab.settings.theme.system"),
    SettingsThemeLight("habitlab.settings.theme.light"),
    SettingsThemeDark("habitlab.settings.theme.dark"),
    MetricPickerScreenRoot("habitlab.metric-picker.screen.root"),
    MetricPickerEnergy("habitlab.metric-picker.action.energy"),
    MetricPickerSleep("habitlab.metric-picker.action.sleep"),
    MetricPickerCancel("habitlab.metric-picker.action.cancel"),
    ConfirmDeleteScreenRoot("habitlab.confirm-delete.screen.root"),
    ConfirmDeleteConfirm("habitlab.confirm-delete.action.confirm"),
    ConfirmDeleteCancel("habitlab.confirm-delete.action.cancel"),
    ConfirmDeleteError("habitlab.confirm-delete.state.error"),
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
            "habitlab.experiment-list.",
            "habitlab.experiment-details.",
            "habitlab.experiment-editor.",
            "habitlab.daily-check-in.",
            "habitlab.settings.",
            "habitlab.metric-picker.",
            "habitlab.confirm-delete.",
        )
    }
}

/** Selectors for DEN-12's real Experiment List root. */
object ExperimentListAutomationIds {
    val screenRoot = AutomationId.ExperimentListScreenRoot
    val openSettings = AutomationId.ExperimentListOpenSettings
    val create = AutomationId.ExperimentListCreate
    val loading = AutomationId.ExperimentListLoading
    val empty = AutomationId.ExperimentListEmpty
    val error = AutomationId.ExperimentListError
    val row = AutomationId.ExperimentListRow
    val dailyMovementRow = AutomationId.ExperimentListDailyMovementRow
    val sleepRoutineRow = AutomationId.ExperimentListSleepRoutineRow
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
