package com.denis.habitlab.shared.presentation.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.denis.habitlab.shared.presentation.gallery.sections.GalleryControlsSection
import com.denis.habitlab.shared.presentation.gallery.sections.GalleryExperimentsSection
import com.denis.habitlab.shared.presentation.gallery.sections.GalleryStatesSection
import com.denis.habitlab.shared.presentation.navigation.rememberNavigationActionDispatcher
import com.denis.habitlab.shared.presentation.ui.automation.ComponentGalleryAutomationIds
import com.denis.habitlab.shared.presentation.ui.component.HabitLabAppScaffold
import com.denis.habitlab.shared.presentation.ui.component.HabitLabDialogShell
import com.denis.habitlab.shared.presentation.ui.component.HabitLabToolbar
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.gallery_back_accessibility_label
import habitlab.shared.generated.resources.gallery_back_action_label
import habitlab.shared.generated.resources.gallery_dialog_confirm_label
import habitlab.shared.generated.resources.gallery_dialog_dismiss_label
import habitlab.shared.generated.resources.gallery_dialog_message
import habitlab.shared.generated.resources.gallery_dialog_title
import habitlab.shared.generated.resources.gallery_intro
import habitlab.shared.generated.resources.gallery_title
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun ComponentGalleryScreen(
    appTitle: String,
    viewModel: ComponentGalleryViewModel,
    isNavigationActionAllowed: () -> Boolean,
    handleNavigationAction: suspend (NavigationEffect) -> Unit,
) {
    val state by viewModel.collectAsState()
    var isDialogVisible by remember { mutableStateOf(false) }

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is NavigationEffect -> handleNavigationAction(effect)
            ViewEffect.ShowDialog -> isDialogVisible = true
            ViewEffect.HideDialog -> isDialogVisible = false
        }
    }

    Content(
        appTitle = appTitle,
        state = state,
        isDialogVisible = isDialogVisible,
        onAction = rememberNavigationActionDispatcher(
            isNavigationActionAllowed = isNavigationActionAllowed,
            requiresResumedEntry = Action::requiresResumedEntry,
            dispatchAction = viewModel::dispatchAction,
        ),
    )
}

@Composable
private fun Content(
    appTitle: String,
    state: ViewState,
    isDialogVisible: Boolean,
    onAction: (Action) -> Unit,
) {
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
                onBack = { onAction(Action.BackClicked) },
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
            GalleryControlsSection(
                habitName = state.habitName,
                onOpenDialog = { onAction(Action.DialogRequested) },
                onStartFlow = { onAction(Action.StartFlowClicked) },
                onHabitNameChanged = { value -> onAction(Action.HabitNameChanged(value)) },
            )
            GalleryExperimentsSection(
                onExperimentClicked = { id -> onAction(Action.ExperimentClicked(id)) },
            )
            GalleryStatesSection()
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
            onConfirm = { onAction(Action.DialogConfirmed) },
            onDismiss = { onAction(Action.DialogDismissed) },
        )
    }
}

private fun Action.requiresResumedEntry(): Boolean = when (this) {
    Action.BackClicked,
    Action.StartFlowClicked,
    is Action.ExperimentClicked,
    -> true
    Action.DialogRequested,
    Action.DialogConfirmed,
    Action.DialogDismissed,
    is Action.HabitNameChanged,
    -> false
}

@Preview
@Composable
private fun Preview() {
    HabitLabTheme {
        Content(
            appTitle = "Habit Lab",
            state = ViewState(habitName = "Morning walk"),
            isDialogVisible = false,
            onAction = {},
        )
    }
}
