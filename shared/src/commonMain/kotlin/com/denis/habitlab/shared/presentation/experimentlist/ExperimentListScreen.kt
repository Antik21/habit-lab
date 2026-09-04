package com.denis.habitlab.shared.presentation.experimentlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.presentation.experimentlist.sections.ExperimentListContentSection
import com.denis.habitlab.shared.presentation.experimentlist.sections.ExperimentListHeaderSection
import com.denis.habitlab.shared.presentation.navigation.rememberNavigationActionDispatcher
import com.denis.habitlab.shared.presentation.ui.automation.ExperimentListAutomationIds
import com.denis.habitlab.shared.presentation.ui.component.HabitLabAppScaffold
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.experiment_list_title
import org.jetbrains.compose.resources.stringResource
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun ExperimentListScreen(
    viewModel: ExperimentListViewModel,
    isNavigationActionAllowed: () -> Boolean,
    handleNavigationAction: suspend (NavigationEffect) -> Unit,
) {
    val state by viewModel.collectAsState()
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
@OptIn(ExperimentalMaterial3Api::class)
private fun Content(state: ViewState, onAction: (Action) -> Unit) {
    HabitLabAppScaffold(
        automationId = ExperimentListAutomationIds.screenRoot,
        toolbar = { TopAppBar(title = { Text(stringResource(Res.string.experiment_list_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)
                .padding(HabitLabSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Medium),
        ) {
            ExperimentListHeaderSection(
                onCreate = { onAction(Action.CreateClicked) },
                onSettings = { onAction(Action.SettingsClicked) },
            )
            ExperimentListContentSection(
                content = state.content,
                onExperimentClicked = { onAction(Action.ExperimentClicked(it)) },
            )
        }
    }
}

@Preview
@Composable
private fun Preview() {
    HabitLabTheme { Content(ViewState(), onAction = {}) }
}
