/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.element.android.features.agentim.impl.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.permissions.api.localnetwork.LocalNetworkPermissionDialogView

@Composable
fun AgentimLoginView(state: AgentimLoginState, onBack: () -> Unit, modifier: Modifier = Modifier, onExisting: () -> Unit = {}) {
    Column(
        modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(text = stringResource(R.string.action_agentim_back), onClick = onBack)
        Text(stringResource(R.string.screen_agentim_login_title))
        Text(stringResource(R.string.screen_agentim_login_help))
        TextField(
            state.homeserver,
            { state.eventSink(AgentimLoginEvent.Homeserver(it)) },
            label = stringResource(R.string.screen_agentim_homeserver),
            singleLine = true,
            enabled = !state.busy
        )
        if (state.registering) {
            TextField(
                state.localpart,
                { state.eventSink(AgentimLoginEvent.Localpart(it)) },
                label = stringResource(R.string.screen_agentim_localpart),
                singleLine = true,
                enabled = !state.busy
            )
            TextField(
                state.displayName,
                { state.eventSink(AgentimLoginEvent.DisplayName(it)) },
                label = stringResource(R.string.screen_agentim_display_name),
                singleLine = true,
                enabled = !state.busy
            )
            TextField(
                state.admission,
                { state.eventSink(AgentimLoginEvent.Admission(it)) },
                label = stringResource(R.string.screen_agentim_admission),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !state.busy
            )
        } else {
            TextField(
                state.token,
                { state.eventSink(AgentimLoginEvent.Token(it)) },
                label = stringResource(R.string.screen_agentim_login_token),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !state.busy
            )
        }
        if (state.failed) Text(stringResource(if (state.originChanged) R.string.error_agentim_homeserver_origin else R.string.error_agentim_login))
        Button(
            text = stringResource(if (state.registering) R.string.screen_agentim_register else R.string.screen_agentim_sign_in),
            onClick = { state.eventSink(AgentimLoginEvent.Submit) },
            showProgress = state.busy,
            enabled = !state.busy && state.homeserver.isNotBlank() &&
                if (state.registering) state.admission.isNotBlank() && state.localpart.isNotBlank() else state.token.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(
            text = stringResource(if (state.registering) R.string.screen_agentim_pair_title else R.string.screen_agentim_register),
            onClick = onExisting,
            enabled = !state.busy
        )
    }
    LocalNetworkPermissionDialogView(
        dialog = state.permissionDialog,
        onSubmit = { state.eventSink(AgentimLoginEvent.RequestPermission) },
        onDismiss = { state.eventSink(AgentimLoginEvent.DismissPermission) },
    )
}

@PreviewsDayNight
@Composable
internal fun AgentimLoginViewPreview() = ElementPreview {
    AgentimLoginView(AgentimLoginState(), {})
}

@PreviewsDayNight
@Composable
internal fun AgentimRegistrationViewPreview() = ElementPreview {
    AgentimLoginView(AgentimLoginState(registering = true, failed = true), {})
}

@PreviewsDayNight
@Composable
internal fun AgentimLoginLoadingPreview() = ElementPreview {
    AgentimLoginView(AgentimLoginState(busy = true), {})
}
