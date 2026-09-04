package com.denis.habitlab.shared.presentation.experimentdetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.presentation.experimentdetails.sections.ExperimentDetailsActionsSection
import com.denis.habitlab.shared.presentation.experimentdetails.sections.ExperimentDetailsContentSection
import com.denis.habitlab.shared.presentation.experimentdetails.sections.ExperimentDetailsSummarySection
import com.denis.habitlab.shared.presentation.navigation.rememberNavigationActionDispatcher
import com.denis.habitlab.shared.presentation.navigation.DeleteDialogResult
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import com.denis.habitlab.shared.presentation.ui.component.HabitLabAppScaffold
import com.denis.habitlab.shared.presentation.ui.component.HabitLabToolbar
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.experiment_details_title
import habitlab.shared.generated.resources.navigation_back_accessibility_label
import habitlab.shared.generated.resources.navigation_back_action_label
import org.jetbrains.compose.resources.stringResource
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun ExperimentDetailsScreen(
    viewModel: ExperimentDetailsViewModel,
    deliveredDeleteResult: DeleteDialogResult?,
    onDeleteResultConsumed: () -> Unit,
    isNavigationActionAllowed: () -> Boolean,
    handleNavigationAction: suspend (NavigationEffect) -> Unit,
) {
    val state by viewModel.collectAsState()
    LaunchedEffect(deliveredDeleteResult) {
        deliveredDeleteResult?.let {
            viewModel.dispatchAction(Action.DeleteResultDelivered(it))
            onDeleteResultConsumed()
        }
    }
    viewModel.collectSideEffect { effect ->
        when (effect) { is NavigationEffect -> handleNavigationAction(effect) }
    }
    Content(
        state = state,
        onAction = rememberNavigationActionDispatcher(
            isNavigationActionAllowed = isNavigationActionAllowed,
            dispatchAction = viewModel::dispatchAction,
        ),
    )
}

@Composable
private fun Content(state: ViewState, onAction: (Action) -> Unit) {
    HabitLabAppScaffold(
        automationId = AutomationId.ExperimentDetailsScreenRoot,
        toolbar = {
            HabitLabToolbar(
                title = stringResource(Res.string.experiment_details_title),
                backActionLabel = stringResource(Res.string.navigation_back_action_label),
                backActionContentDescription = stringResource(Res.string.navigation_back_accessibility_label),
                backAutomationId = AutomationId.ExperimentDetailsBack,
                onBack = { onAction(Action.BackClicked) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(HabitLabSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Medium),
        ) {
            ExperimentDetailsContentSection(state.content)
            (state.content as? ContentUiModel.Available)?.let { available ->
                ExperimentDetailsSummarySection(available.name, available.status)
                ExperimentDetailsActionsSection(
                    status = available.status,
                    onEdit = { onAction(Action.EditClicked) },
                    onCheckIn = { onAction(Action.DailyCheckInClicked) },
                    onDelete = { onAction(Action.DeleteClicked) },
                )
            }
        }
    }
}

@Preview
@Composable
private fun Preview() {
    HabitLabTheme { Content(ViewState(com.denis.habitlab.shared.domain.model.ExperimentId("daily-movement")), {}) }
}
