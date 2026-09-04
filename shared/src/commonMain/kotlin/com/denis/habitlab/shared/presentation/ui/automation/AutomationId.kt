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
    ;

    internal companion object {
        fun validateContract() {
            val values = entries.map(AutomationId::value)
            require(values.all { it.startsWith(GALLERY_NAMESPACE) }) {
                "Automation IDs must use the habitlab.gallery namespace."
            }
            require(values.toSet().size == values.size) {
                "Automation IDs must be unique."
            }
        }

        private const val GALLERY_NAMESPACE = "habitlab.gallery."
    }
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
