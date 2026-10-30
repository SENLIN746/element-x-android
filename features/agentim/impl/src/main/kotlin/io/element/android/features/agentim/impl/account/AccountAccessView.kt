/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.features.agentim.impl.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton

@Composable
fun AccountAccessView(state: AccountAccessState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { state.eventSink(AccountAccessEvent.Export(it)) }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { state.eventSink(AccountAccessEvent.Import(it)) }
    }
    Column(modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(stringResource(R.string.action_agentim_back), onClick = onBack, enabled = !state.busy)
        Text(stringResource(R.string.screen_agentim_account_title))
        Text(stringResource(R.string.screen_agentim_account_help))
        if (state.busy) Text(stringResource(R.string.screen_agentim_loading))
        if (state.failed) Text(stringResource(R.string.error_agentim_request))
        if (state.verified) Text(stringResource(R.string.screen_agentim_account_verified))
        if (state.revoked) Text(stringResource(R.string.screen_agentim_account_revoked))
        if (state.signedIn) {
            TextButton(
                stringResource(R.string.screen_agentim_account_export),
                enabled = !state.busy,
                onClick = { export.launch("agentim-account.json") }
            )
            TextButton(
                stringResource(R.string.screen_agentim_account_verify),
                enabled = !state.busy,
                onClick = { restore.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
            )
            TextButton(
                stringResource(R.string.screen_agentim_account_revoke),
                enabled = !state.busy && !state.revoked,
                onClick = { state.eventSink(AccountAccessEvent.Revoke) }
            )
        } else {
            TextButton(
                stringResource(R.string.screen_agentim_account_restore),
                enabled = !state.busy,
                onClick = { restore.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
            )
            if (state.pending) {
                TextButton(
                    stringResource(R.string.screen_agentim_account_resume),
                    enabled = !state.busy,
                onClick = { state.eventSink(AccountAccessEvent.Resume) }
                )
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun AccountBackupPreview() = ElementPreview { AccountAccessView(AccountAccessState(signedIn = true, verified = true), {}) }

@PreviewsDayNight
@Composable
internal fun AccountRestorePreview() = ElementPreview { AccountAccessView(AccountAccessState(pending = true), {}) }
