package com.denis.habitlab.shared.presentation.gallery.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.denis.habitlab.shared.presentation.ui.automation.ComponentGalleryAutomationIds
import com.denis.habitlab.shared.presentation.ui.component.HabitLabEmptyBlock
import com.denis.habitlab.shared.presentation.ui.component.HabitLabErrorBlock
import com.denis.habitlab.shared.presentation.ui.component.HabitLabLoadingBlock
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabSpacing
import com.denis.habitlab.shared.presentation.ui.theme.HabitLabTheme
import habitlab.shared.generated.resources.Res
import habitlab.shared.generated.resources.gallery_empty_accessibility_label
import habitlab.shared.generated.resources.gallery_empty_message
import habitlab.shared.generated.resources.gallery_empty_title
import habitlab.shared.generated.resources.gallery_error_accessibility_label
import habitlab.shared.generated.resources.gallery_error_message
import habitlab.shared.generated.resources.gallery_error_title
import habitlab.shared.generated.resources.gallery_loading_accessibility_label
import habitlab.shared.generated.resources.gallery_loading_title
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun GalleryStatesSection() {
    Column(verticalArrangement = Arrangement.spacedBy(HabitLabSpacing.Medium)) {
        HabitLabLoadingBlock(
            title = stringResource(Res.string.gallery_loading_title),
            accessibilityLabel = stringResource(Res.string.gallery_loading_accessibility_label),
            automationId = ComponentGalleryAutomationIds.loadingState,
        )
        HabitLabEmptyBlock(
            title = stringResource(Res.string.gallery_empty_title),
            message = stringResource(Res.string.gallery_empty_message),
            accessibilityLabel = stringResource(Res.string.gallery_empty_accessibility_label),
            automationId = ComponentGalleryAutomationIds.emptyState,
        )
        HabitLabErrorBlock(
            title = stringResource(Res.string.gallery_error_title),
            message = stringResource(Res.string.gallery_error_message),
            accessibilityLabel = stringResource(Res.string.gallery_error_accessibility_label),
            automationId = ComponentGalleryAutomationIds.errorState,
        )
    }
}

@Preview
@Composable
private fun Preview() {
    HabitLabTheme { GalleryStatesSection() }
}
