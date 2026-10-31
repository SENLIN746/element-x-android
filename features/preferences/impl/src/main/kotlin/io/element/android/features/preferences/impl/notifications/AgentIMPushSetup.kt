/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
package io.element.android.features.preferences.impl.notifications

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text

/** Registration is distinct from permission, verification and delivery. */
@Composable
internal fun AgentIMPushSetup(state: NotificationSettingsState, onVerify: () -> Unit, onTroubleshoot: () -> Unit) {
    if (!state.appSettings.appNotificationsEnabled || state.pushRegistrationState == PushRegistrationState.Disabled) return
    val uri = LocalUriHandler.current
    val (title, description, action) = when (state.pushRegistrationState) {
        PushRegistrationState.Checking -> Triple(R.string.agentim_push_checking, null, {})
        PushRegistrationState.NeedsVerification -> Triple(R.string.agentim_push_verify, R.string.agentim_push_verify_detail, onVerify)
        PushRegistrationState.NeedsDistributor -> Triple(
            R.string.agentim_push_distributor,
            R.string.agentim_push_distributor_detail,
            { uri.openUri("https://f-droid.org/packages/io.heckel.ntfy/") }
        )
        PushRegistrationState.Registered -> Triple(R.string.agentim_push_registered, R.string.agentim_push_registered_detail, onTroubleshoot)
        PushRegistrationState.Failed -> Triple(
            R.string.agentim_push_retry,
            R.string.agentim_push_failed,
            { state.eventSink(NotificationSettingsEvent.RetryPushRegistration) }
        )
        PushRegistrationState.Disabled -> return
    }
    ListItem(
        content = { Text(stringResource(title)) },
        supportingContent = description?.let { { Text(stringResource(it)) } },
        onClick = action
    )
}

internal class PushSetupStatePreviewParam : PreviewParameterProvider<NotificationSettingsState> {
    override val values = PushRegistrationState.entries.filter { it != PushRegistrationState.Disabled }.asSequence()
        .map { aValidNotificationSettingsState().copy(pushRegistrationState = it) }
}

@PreviewsDayNight
@Composable
internal fun AgentIMPushSetupPreview(@PreviewParameter(PushSetupStatePreviewParam::class) state: NotificationSettingsState) = ElementPreview {
    AgentIMPushSetup(state, {}, {})
}
