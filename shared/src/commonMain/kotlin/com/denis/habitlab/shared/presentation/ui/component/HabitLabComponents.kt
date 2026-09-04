package com.denis.habitlab.shared.presentation.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.denis.habitlab.shared.presentation.ui.automation.AutomationId
import com.denis.habitlab.shared.presentation.ui.automation.autodevId
import com.denis.habitlab.shared.presentation.ui.automation.enableAutodevResourceIds
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing

@Composable
fun HabitLabAppScaffold(
    automationId: AutomationId,
    toolbar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier
            .enableAutodevResourceIds()
            .autodevId(automationId),
        topBar = toolbar,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitLabToolbar(
    title: String,
    backActionLabel: String,
    backActionContentDescription: String,
    backAutomationId: AutomationId,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = { Text(text = title) },
        navigationIcon = {
            IconButton(
                modifier = Modifier
                    .autodevId(backAutomationId)
                    .semantics { contentDescription = backActionContentDescription },
                onClick = onBack,
            ) {
                Text(text = backActionLabel)
            }
        },
    )
}

@Composable
fun HabitLabPrimaryButton(
    label: String,
    automationId: AutomationId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier.autodevId(automationId),
        onClick = onClick,
    ) {
        Text(text = label)
    }
}

@Composable
fun HabitLabSecondaryButton(
    label: String,
    automationId: AutomationId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        modifier = modifier.autodevId(automationId),
        onClick = onClick,
    ) {
        Text(text = label)
    }
}

@Composable
fun HabitLabTextField(
    value: String,
    label: String,
    accessibilityLabel: String,
    automationId: AutomationId,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        modifier = modifier
            .fillMaxWidth()
            .autodevId(automationId)
            .semantics { contentDescription = accessibilityLabel },
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        singleLine = true,
    )
}

@Composable
fun HabitLabClickableListRow(
    title: String,
    supportingText: String,
    accessibilityLabel: String,
    automationId: AutomationId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .autodevId(automationId)
            .clickable(onClick = onClick)
            .semantics { contentDescription = accessibilityLabel },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(HabitLabSpacing.Medium),
    ) {
        Column(modifier = Modifier.padding(HabitLabSpacing.Medium)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(HabitLabSpacing.ExtraSmall))
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun HabitLabLoadingBlock(
    title: String,
    accessibilityLabel: String,
    automationId: AutomationId,
    modifier: Modifier = Modifier,
) {
    StateBlock(
        automationId = automationId,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(HabitLabSpacing.Large)
                    .semantics { contentDescription = accessibilityLabel },
            )
            Spacer(modifier = Modifier.width(HabitLabSpacing.Small))
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun HabitLabEmptyBlock(
    title: String,
    message: String,
    accessibilityLabel: String,
    automationId: AutomationId,
    modifier: Modifier = Modifier,
) {
    StateBlock(automationId = automationId, modifier = modifier) {
        Text(
            modifier = Modifier.semantics { contentDescription = accessibilityLabel },
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(HabitLabSpacing.ExtraSmall))
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun HabitLabErrorBlock(
    title: String,
    message: String,
    accessibilityLabel: String,
    automationId: AutomationId,
    modifier: Modifier = Modifier,
) {
    StateBlock(automationId = automationId, modifier = modifier) {
        Text(
            modifier = Modifier.semantics { contentDescription = accessibilityLabel },
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(HabitLabSpacing.ExtraSmall))
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun HabitLabDialogShell(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    automationId: AutomationId,
    confirmAutomationId: AutomationId,
    dismissAutomationId: AutomationId,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier
            .enableAutodevResourceIds()
            .autodevId(automationId),
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            HabitLabPrimaryButton(
                label = confirmLabel,
                automationId = confirmAutomationId,
                onClick = onConfirm,
            )
        },
        dismissButton = {
            HabitLabSecondaryButton(
                label = dismissLabel,
                automationId = dismissAutomationId,
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun StateBlock(
    automationId: AutomationId,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .autodevId(automationId),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(HabitLabSpacing.Medium),
    ) {
        Box(modifier = Modifier.padding(HabitLabSpacing.Medium)) {
            Column(verticalArrangement = Arrangement.Center) {
                content()
            }
        }
    }
}
