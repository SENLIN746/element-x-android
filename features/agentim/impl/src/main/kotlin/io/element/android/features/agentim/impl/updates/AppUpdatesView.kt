/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.updates

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.lifecycle.Lifecycle
import io.element.android.features.agentim.impl.R
import io.element.android.libraries.agentim.api.updates.AppUpdatePhase
import io.element.android.libraries.agentim.api.updates.AppUpdateStatus
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.OnLifecycleEvent

@Composable
internal fun AppUpdatesView(state: AppUpdatesState) {
    OnLifecycleEvent { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> state.action(AppUpdateAction.Refresh)
            Lifecycle.Event.ON_PAUSE -> state.action(AppUpdateAction.Pause)
            else -> Unit
        }
    }
    val phase = state.status.phase
    val title = when {
        state.busy -> R.string.agentim_update_busy
        state.failed -> R.string.agentim_update_failed
        else -> when (phase) {
            AppUpdatePhase.Current -> R.string.agentim_update_check
            AppUpdatePhase.Available -> R.string.agentim_update_download
            AppUpdatePhase.Downloading -> R.string.agentim_update_downloading
            AppUpdatePhase.Ready -> R.string.agentim_update_install
            AppUpdatePhase.InstallPermission -> R.string.agentim_update_permission
            AppUpdatePhase.Store -> R.string.agentim_update_store
        }
    }
    val action = when (phase) {
        AppUpdatePhase.Current -> AppUpdateAction.Check
        AppUpdatePhase.Available -> AppUpdateAction.Download
        AppUpdatePhase.Downloading -> AppUpdateAction.Refresh
        AppUpdatePhase.Ready, AppUpdatePhase.InstallPermission, AppUpdatePhase.Store -> AppUpdateAction.Install
    }
    val description = if (state.failed) {
        stringResource(R.string.agentim_update_retry_detail)
    } else {
        state.status.update?.let { "${it.version} · ${state.status.percent}%" }
            ?: stringResource(R.string.agentim_update_detail)
    }
    ListItem(
        content = { Text(stringResource(title)) },
        supportingContent = { Text(description) },
        onClick = { if (!state.busy) state.action(if (state.failed) AppUpdateAction.Check else action) },
    )
    if (phase == AppUpdatePhase.Downloading || phase == AppUpdatePhase.Ready || phase == AppUpdatePhase.InstallPermission || state.failed) {
        ListItem(content = { Text(stringResource(R.string.agentim_update_cancel)) }, onClick = { state.action(AppUpdateAction.Cancel) })
    }
}

internal class AppUpdatesPreview : PreviewParameterProvider<AppUpdatesState> {
    override val values = AppUpdatePhase.entries.asSequence().map { AppUpdatesState(AppUpdateStatus(it)) } +
        sequenceOf(AppUpdatesState(busy = true), AppUpdatesState(failed = true))
}

@PreviewsDayNight
@Composable
internal fun AppUpdatesViewPreview(@PreviewParameter(AppUpdatesPreview::class) state: AppUpdatesState) = ElementPreview { AppUpdatesView(state) }
