/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.pairing

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
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.permissions.api.localnetwork.LocalNetworkPermissionDialogView

/** Stateless view: only public pairing metadata is rendered or copied. */
@Composable
fun AgentimPairingView(state: AgentimPairingState, onBack: () -> Unit, onCreate: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(stringResource(R.string.action_agentim_back), onClick = onBack)
        Text(stringResource(R.string.screen_agentim_pair_title))
        Text(stringResource(R.string.screen_agentim_pair_help))
        if (!state.busy || state.request == null) {
            TextField(
                state.homeserver,
                { state.eventSink(AgentimPairingEvent.Homeserver(it)) },
                label = stringResource(R.string.screen_agentim_homeserver),
                singleLine = true,
                enabled = !state.busy
            )
            TextField(
                state.targetUserId,
                { state.eventSink(AgentimPairingEvent.Target(it)) },
                label = stringResource(R.string.screen_agentim_pair_target),
                singleLine = true,
                enabled = !state.busy
            )
            TextField(
                state.deviceName,
                { state.eventSink(AgentimPairingEvent.DeviceName(it)) },
                label = stringResource(R.string.screen_agentim_pair_device),
                singleLine = true,
                enabled = !state.busy
            )
        } else {
            Text(state.targetUserId)
            Text(state.deviceName)
        }
        val status = when (state.phase) {
            PairingScreenPhase.Idle -> null
            PairingScreenPhase.Creating -> R.string.screen_agentim_pair_creating
            PairingScreenPhase.Waiting -> R.string.screen_agentim_pair_waiting
            PairingScreenPhase.Connecting -> R.string.screen_agentim_pair_connecting
            PairingScreenPhase.Connected -> R.string.screen_agentim_pair_connected
            PairingScreenPhase.Rejected -> R.string.error_agentim_pair_rejected
            PairingScreenPhase.Expired -> R.string.error_agentim_pair_expired
            PairingScreenPhase.Failed -> R.string.error_agentim_pair_failed
        }
        status?.let { Text(stringResource(it)) }
        if (state.phase == PairingScreenPhase.Waiting) {
            state.request?.let { request ->
            Text(request.userCode)
            TextButton(stringResource(R.string.screen_agentim_pair_details), onClick = { state.eventSink(AgentimPairingEvent.ToggleDetails) })
            if (state.detailsVisible) Text(request.fingerprint())
            Text(stringResource(R.string.screen_agentim_pair_context))
            TextButton(stringResource(R.string.screen_agentim_pair_copy), onClick = { state.eventSink(AgentimPairingEvent.CopyFingerprint) })
            Text(stringResource(R.string.screen_agentim_pair_expires, state.seconds))
        }
        }
        if (state.phase !in setOf(PairingScreenPhase.Connecting, PairingScreenPhase.Connected)) {
            if (state.busy) {
                TextButton(stringResource(R.string.screen_agentim_pair_cancel), onClick = { state.eventSink(AgentimPairingEvent.Cancel) })
            } else {
                Button(
                    stringResource(R.string.screen_agentim_pair_start),
                    onClick = { state.eventSink(AgentimPairingEvent.Submit) },
                    enabled = state.homeserver.isNotBlank() && state.targetUserId.isNotBlank() && state.deviceName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(stringResource(R.string.screen_agentim_register), onClick = onCreate)
            }
        }
        Text(stringResource(R.string.screen_agentim_pair_boundary))
    }
    LocalNetworkPermissionDialogView(
        dialog = state.permissionDialog,
        onSubmit = { state.eventSink(AgentimPairingEvent.RequestPermission) },
        onDismiss = { state.eventSink(AgentimPairingEvent.DismissPermission) }
    )
}
