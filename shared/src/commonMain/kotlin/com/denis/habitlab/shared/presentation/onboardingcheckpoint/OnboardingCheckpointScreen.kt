package com.denis.habitlab.shared.presentation.onboardingcheckpoint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.denis.habitlab.shared.domain.model.OnboardingStep
import com.denis.habitlab.shared.presentation.navigation.rememberNavigationActionDispatcher
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import com.denis.habitlab.shared.presentation.ui.automation.autodevId
import com.denis.habitlab.shared.presentation.ui.component.HabitLabAppScaffold
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.onboarding_checkpoint_back
import habitlab.shared.generated.resources.onboarding_checkpoint_context
import habitlab.shared.generated.resources.onboarding_checkpoint_health_explanation
import habitlab.shared.generated.resources.onboarding_checkpoint_outcome
import habitlab.shared.generated.resources.onboarding_checkpoint_protocols
import habitlab.shared.generated.resources.onboarding_checkpoint_setup
import habitlab.shared.generated.resources.onboarding_checkpoint_status_coverage
import habitlab.shared.generated.resources.onboarding_checkpoint_welcome
import org.jetbrains.compose.resources.stringResource
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/**
 * Neutral, common contour for DEN-26/25/27/29/30/31/28. It only makes every typed checkpoint
 * observable to Nav3 until each owning issue replaces this entry with its product screen.
 */
@Composable
fun OnboardingCheckpointScreen(
    viewModel: OnboardingCheckpointViewModel,
    isNavigationActionAllowed: () -> Boolean,
    handleNavigationAction: suspend (NavigationEffect) -> Unit,
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { effect ->
        when (effect) {
            is NavigationEffect -> handleNavigationAction(effect)
        }
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
    HabitLabAppScaffold(automationId = state.checkpoint.automationId, toolbar = {}) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(HabitLabSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Medium),
        ) {
            Text(checkpointTitle(state.checkpoint.step))
            if (state.checkpoint.step != OnboardingStep.WELCOME) {
                Button(
                    onClick = { onAction(Action.BackClicked) },
                    modifier = Modifier.autodevId(AutomationId.OnboardingCheckpointBack),
                ) {
                    Text(stringResource(Res.string.onboarding_checkpoint_back))
                }
            }
        }
    }
}

@Composable
private fun checkpointTitle(step: OnboardingStep): String = stringResource(
    when (step) {
        OnboardingStep.WELCOME -> Res.string.onboarding_checkpoint_welcome
        OnboardingStep.OUTCOME -> Res.string.onboarding_checkpoint_outcome
        OnboardingStep.CONTEXT -> Res.string.onboarding_checkpoint_context
        OnboardingStep.PROTOCOLS -> Res.string.onboarding_checkpoint_protocols
        OnboardingStep.HEALTH_EXPLANATION -> Res.string.onboarding_checkpoint_health_explanation
        OnboardingStep.STATUS_COVERAGE -> Res.string.onboarding_checkpoint_status_coverage
        OnboardingStep.SETUP -> Res.string.onboarding_checkpoint_setup
    },
)

@Preview
@Composable
private fun Preview() {
    HabitLabTheme {
        Content(
            ViewState(CheckpointUiModel(OnboardingStep.OUTCOME, AutomationId.OnboardingOutcomeScreenRoot)),
            onAction = {},
        )
    }
}
