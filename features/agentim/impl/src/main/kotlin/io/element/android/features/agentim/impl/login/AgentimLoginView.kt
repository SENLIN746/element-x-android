/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.features.agentim.impl.R
import io.element.android.libraries.agentim.api.onboarding.AgentimRegistrationPolicy
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.permissions.api.localnetwork.LocalNetworkPermissionDialogView

@Composable
fun AgentimLoginView(state: AgentimLoginState, onBack: () -> Unit, modifier: Modifier = Modifier, onExisting: () -> Unit = {}) {
    BackHandler(enabled = state.busy) { /* A bounded native handoff owns the login slot. */ }
    Column(
        modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(text = stringResource(R.string.action_agentim_back), onClick = onBack, enabled = !state.busy)
        Text(stringResource(R.string.screen_agentim_login_title))
        Text(stringResource(R.string.screen_agentim_login_help))
        Text(stringResource(R.string.screen_agentim_account_help))
        TextField(
            state.homeserver,
            { state.eventSink(AgentimLoginEvent.Homeserver(it)) },
            label = stringResource(R.string.screen_agentim_homeserver),
            singleLine = true,
            enabled = !state.busy && !state.resuming,
        )
        TextField(
            state.localpart,
            { state.eventSink(AgentimLoginEvent.Localpart(it)) },
            label = stringResource(R.string.screen_agentim_localpart),
            singleLine = true,
            enabled = !state.busy && !state.resuming,
        )
        TextField(
            state.displayName,
            { state.eventSink(AgentimLoginEvent.DisplayName(it)) },
            label = stringResource(R.string.screen_agentim_display_name),
            singleLine = true,
            enabled = !state.busy && !state.resuming,
        )
        Text(stringResource(
            when {
            state.resuming -> R.string.screen_agentim_registration_resume_help
            state.policyLoading -> R.string.screen_agentim_loading
            state.policy == AgentimRegistrationPolicy.Open -> R.string.screen_agentim_registration_open
            state.policy == AgentimRegistrationPolicy.Closed -> R.string.screen_agentim_registration_closed
            else -> R.string.screen_agentim_registration_unknown
        }
        ))
        if (!state.policyLoading && state.policy == null) {
            TextButton(stringResource(R.string.action_agentim_refresh), { state.eventSink(AgentimLoginEvent.RefreshPolicy) })
        }
        if (state.failed) Text(stringResource(if (state.nameUnavailable) R.string.error_agentim_name_unavailable else R.string.error_agentim_login))
        Button(
            text = stringResource(if (state.resuming) R.string.screen_agentim_registration_resume else R.string.screen_agentim_registration_connect),
            onClick = { state.eventSink(AgentimLoginEvent.Submit) },
            showProgress = state.busy,
            enabled = !state.busy && (state.resuming || state.policy == AgentimRegistrationPolicy.Open) && state.localpart.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(stringResource(R.string.screen_agentim_pair_title), onExisting, enabled = !state.busy)
    }
    LocalNetworkPermissionDialogView(
        dialog = state.permissionDialog,
        onSubmit = { state.eventSink(AgentimLoginEvent.RequestPermission) },
        onDismiss = { state.eventSink(AgentimLoginEvent.DismissPermission) },
    )
}

@PreviewsDayNight
@Composable
internal fun AgentimLoginViewPreview() = ElementPreview { AgentimLoginView(AgentimLoginState(), {}) }

@PreviewsDayNight
@Composable
internal fun AgentimRegistrationViewPreview() = ElementPreview {
    AgentimLoginView(AgentimLoginState(policy = AgentimRegistrationPolicy.Open, policyLoading = false, failed = true), {})
}

@PreviewsDayNight
@Composable
internal fun AgentimLoginLoadingPreview() = ElementPreview { AgentimLoginView(AgentimLoginState(busy = true), {}) }

@PreviewsDayNight
@Composable
internal fun AgentimRegistrationClosedPreview() = ElementPreview {
    AgentimLoginView(AgentimLoginState(policy = AgentimRegistrationPolicy.Closed, policyLoading = false), {})
}

@PreviewsDayNight
@Composable
internal fun AgentimRegistrationResumePreview() = ElementPreview { AgentimLoginView(AgentimLoginState(resuming = true), {}) }
