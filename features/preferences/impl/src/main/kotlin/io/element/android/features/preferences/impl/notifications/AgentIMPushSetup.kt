/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
package io.element.android.features.preferences.impl.notifications

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text

/** An enabled notification switch is not proof that a background push service is configured. */
@Composable
internal fun AgentIMPushSetup(state: NotificationSettingsState) {
    if (!state.appSettings.appNotificationsEnabled || state.availablePushDistributors.isNotEmpty() || !state.currentPushDistributor.isFailure()) return
    val uriHandler = LocalUriHandler.current
    ListItem(
        content = { Text(stringResource(R.string.screen_notification_settings_agentim_push_missing)) },
        supportingContent = { Text(stringResource(R.string.screen_notification_settings_agentim_push_setup)) },
        onClick = { uriHandler.openUri("https://unifiedpush.org/users/distributors/") },
    )
}

@PreviewsDayNight
@Composable
internal fun AgentIMPushMissingPreview() = ElementPreview {
    AgentIMPushSetup(
        aValidNotificationSettingsState(
            availablePushDistributors = emptyList(),
            currentPushDistributor = AsyncData.Failure(IllegalStateException("No push distributor"))
        )
    )
}
