package com.denis.habitlab.shared.presentation.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.denis.habitlab.shared.presentation.ui.automation.ComponentGalleryAutomationIds
import com.denis.habitlab.shared.presentation.ui.component.HabitLabAppScaffold
import com.denis.habitlab.shared.presentation.ui.component.HabitLabClickableListRow
import com.denis.habitlab.shared.presentation.ui.component.HabitLabDialogShell
import com.denis.habitlab.shared.presentation.ui.component.HabitLabEmptyBlock
import com.denis.habitlab.shared.presentation.ui.component.HabitLabErrorBlock
import com.denis.habitlab.shared.presentation.ui.component.HabitLabLoadingBlock
import com.denis.habitlab.shared.presentation.ui.component.HabitLabPrimaryButton
import com.denis.habitlab.shared.presentation.ui.component.HabitLabSecondaryButton
import com.denis.habitlab.shared.presentation.ui.component.HabitLabTextField
import com.denis.habitlab.shared.presentation.ui.component.HabitLabToolbar
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.gallery_back_accessibility_label
import habitlab.shared.generated.resources.gallery_back_action_label
import habitlab.shared.generated.resources.gallery_dialog_confirm_label
import habitlab.shared.generated.resources.gallery_dialog_dismiss_label
import habitlab.shared.generated.resources.gallery_dialog_message
import habitlab.shared.generated.resources.gallery_dialog_title
import habitlab.shared.generated.resources.gallery_empty_accessibility_label
import habitlab.shared.generated.resources.gallery_empty_message
import habitlab.shared.generated.resources.gallery_empty_title
import habitlab.shared.generated.resources.gallery_error_accessibility_label
import habitlab.shared.generated.resources.gallery_error_message
import habitlab.shared.generated.resources.gallery_error_title
import habitlab.shared.generated.resources.gallery_field_accessibility_label
import habitlab.shared.generated.resources.gallery_field_label
import habitlab.shared.generated.resources.gallery_intro
import habitlab.shared.generated.resources.gallery_loading_accessibility_label
import habitlab.shared.generated.resources.gallery_loading_title
import habitlab.shared.generated.resources.gallery_primary_action_label
import habitlab.shared.generated.resources.gallery_row_daily_movement_accessibility_label
import habitlab.shared.generated.resources.gallery_row_daily_movement_subtitle
import habitlab.shared.generated.resources.gallery_row_daily_movement_title
import habitlab.shared.generated.resources.gallery_row_sleep_routine_accessibility_label
import habitlab.shared.generated.resources.gallery_row_sleep_routine_subtitle
import habitlab.shared.generated.resources.gallery_row_sleep_routine_title
import habitlab.shared.generated.resources.gallery_secondary_action_label
import habitlab.shared.generated.resources.gallery_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun ComponentGalleryScreen(appTitle: String) {
    ComponentGalleryScreen(
        appTitle = appTitle,
        onBack = {},
        onOpenExperiment = {},
        onStartFlow = {},
        experimentRows = ComponentGalleryExperimentRows(),
    )
}

/**
 * The gallery remains the app root. Navigation callbacks are supplied by the app-owned Nav3 host
 * so this presentation component never owns a back stack or platform behavior.
 */
@Composable
fun ComponentGalleryScreen(
    appTitle: String,
    onBack: () -> Unit,
    onOpenExperiment: (String) -> Unit,
    onStartFlow: () -> Unit,
    experimentRows: ComponentGalleryExperimentRows,
) {
    var habitName by remember { mutableStateOf("") }
    var isDialogVisible by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        HabitLabAppScaffold(
            automationId = ComponentGalleryAutomationIds.screenRoot,
            toolbar = {
                HabitLabToolbar(
                    title = stringResource(Res.string.gallery_title, appTitle),
                    backActionLabel = stringResource(Res.string.gallery_back_action_label),
                    backActionContentDescription = stringResource(
                        Res.string.gallery_back_accessibility_label,
                    ),
                    backAutomationId = ComponentGalleryAutomationIds.toolbarBack,
                    onBack = onBack,
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .padding(HabitLabSpacing.Large),
                verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Medium),
            ) {
                    Text(
                        text = stringResource(Res.string.gallery_intro),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    HabitLabPrimaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(Res.string.gallery_primary_action_label),
                        automationId = ComponentGalleryAutomationIds.primaryAction,
                        onClick = { isDialogVisible = true },
                    )
                    HabitLabSecondaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(Res.string.gallery_secondary_action_label),
                        automationId = ComponentGalleryAutomationIds.secondaryAction,
                        onClick = onStartFlow,
                    )
                    HabitLabTextField(
                        value = habitName,
                        label = stringResource(Res.string.gallery_field_label),
                        accessibilityLabel = stringResource(
                            Res.string.gallery_field_accessibility_label,
                        ),
                        automationId = ComponentGalleryAutomationIds.textField,
                        onValueChange = { habitName = it },
                    )
                    when {
                        experimentRows.isLoading -> HabitLabLoadingBlock(
                            title = stringResource(Res.string.gallery_loading_title),
                            accessibilityLabel = stringResource(
                                Res.string.gallery_loading_accessibility_label,
                            ),
                            automationId = ComponentGalleryAutomationIds.loadingState,
                        )

                        experimentRows.isError -> HabitLabErrorBlock(
                            title = stringResource(Res.string.gallery_error_title),
                            message = stringResource(Res.string.gallery_error_message),
                            accessibilityLabel = stringResource(
                                Res.string.gallery_error_accessibility_label,
                            ),
                            automationId = ComponentGalleryAutomationIds.errorState,
                        )

                        !experimentRows.hasDailyMovement && !experimentRows.hasSleepRoutine -> {
                            HabitLabEmptyBlock(
                                title = stringResource(Res.string.gallery_empty_title),
                                message = stringResource(Res.string.gallery_empty_message),
                                accessibilityLabel = stringResource(
                                    Res.string.gallery_empty_accessibility_label,
                                ),
                                automationId = ComponentGalleryAutomationIds.emptyState,
                            )
                        }

                        else -> {
                            if (experimentRows.hasDailyMovement) {
                                HabitLabClickableListRow(
                                    title = stringResource(Res.string.gallery_row_daily_movement_title),
                                    supportingText = stringResource(
                                        Res.string.gallery_row_daily_movement_subtitle,
                                    ),
                                    accessibilityLabel = stringResource(
                                        Res.string.gallery_row_daily_movement_accessibility_label,
                                    ),
                                    automationId = ComponentGalleryAutomationIds.firstRow,
                                    onClick = { onOpenExperiment(DAILY_MOVEMENT_EXPERIMENT_ID) },
                                )
                            }
                            if (experimentRows.hasSleepRoutine) {
                                HabitLabClickableListRow(
                                    title = stringResource(Res.string.gallery_row_sleep_routine_title),
                                    supportingText = stringResource(
                                        Res.string.gallery_row_sleep_routine_subtitle,
                                    ),
                                    accessibilityLabel = stringResource(
                                        Res.string.gallery_row_sleep_routine_accessibility_label,
                                    ),
                                    automationId = ComponentGalleryAutomationIds.secondRow,
                                    onClick = { onOpenExperiment(SLEEP_ROUTINE_EXPERIMENT_ID) },
                                )
                            }
                        }
                    }
            }
        }
        if (isDialogVisible) {
            HabitLabDialogShell(
                title = stringResource(Res.string.gallery_dialog_title),
                message = stringResource(Res.string.gallery_dialog_message),
                confirmLabel = stringResource(Res.string.gallery_dialog_confirm_label),
                dismissLabel = stringResource(Res.string.gallery_dialog_dismiss_label),
                automationId = ComponentGalleryAutomationIds.dialogRoot,
                confirmAutomationId = ComponentGalleryAutomationIds.dialogConfirm,
                dismissAutomationId = ComponentGalleryAutomationIds.dialogDismiss,
                onConfirm = { isDialogVisible = false },
                onDismiss = { isDialogVisible = false },
            )
        }
    }
}

/** Fixed selector-backed demo rows are visible only when those persisted IDs exist. */
data class ComponentGalleryExperimentRows(
    val isLoading: Boolean = false,
    val hasDailyMovement: Boolean = false,
    val hasSleepRoutine: Boolean = false,
    val isError: Boolean = false,
)

private const val DAILY_MOVEMENT_EXPERIMENT_ID = "daily-movement"
private const val SLEEP_ROUTINE_EXPERIMENT_ID = "sleep-routine"
