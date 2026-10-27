/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.pairing

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.zacsweers.metro.Inject
import io.element.android.features.agentim.impl.login.AgentimDeviceLogin
import io.element.android.libraries.agentim.api.pairing.AgentimPairingService
import io.element.android.libraries.androidutils.clipboard.ClipboardHelper
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.permissions.api.PermissionsEvent
import io.element.android.libraries.permissions.api.PermissionsPresenter
import io.element.android.libraries.permissions.api.localnetwork.LocalNetworkPermissionAdvisor
import io.element.android.libraries.permissions.api.localnetwork.LocalNetworkPermissionDialog
import kotlinx.coroutines.launch

@Inject
class AgentimPairingPresenter(
    private val service: AgentimPairingService,
    private val login: AgentimDeviceLogin,
    private val clipboard: ClipboardHelper,
    permissionsFactory: PermissionsPresenter.Factory,
    private val permissionAdvisor: LocalNetworkPermissionAdvisor,
) : Presenter<AgentimPairingState> {
    private val permissionsPresenter = permissionsFactory.create(Manifest.permission.ACCESS_LOCAL_NETWORK)

    @Composable
    override fun present(): AgentimPairingState {
        val scope = rememberCoroutineScope()
        val controller = remember { AgentimPairingController(service, login, scope) }
        val state by controller.state.collectAsState()
        val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
        val permission = permissionsPresenter.present()
        var pendingPermission by remember { mutableStateOf(false) }
        LaunchedEffect(lifecycle) { controller.active(lifecycle.isAtLeast(Lifecycle.State.RESUMED)) }
        DisposableEffect(controller) { onDispose { controller.cancel() } }
        LaunchedEffect(permission.permissionGranted, pendingPermission) {
            if (permission.permissionGranted && pendingPermission) {
                pendingPermission = false
                controller.submit()
            }
        }
        val dialog = when {
            !pendingPermission || permission.permissionGranted -> LocalNetworkPermissionDialog.None
            permission.shouldShowRationale || !permission.permissionAlreadyAsked -> LocalNetworkPermissionDialog.Rationale
            else -> LocalNetworkPermissionDialog.Settings
        }
        return state.copy(permissionDialog = dialog, eventSink = { event ->
            when (event) {
                AgentimPairingEvent.ToggleDetails -> controller.toggleDetails()
                AgentimPairingEvent.CopyFingerprint -> state.request?.let { clipboard.copyPlainText(it.fingerprint(), onSuccessOnOldDevice = {}) }.let { }
                AgentimPairingEvent.Cancel -> {
                    pendingPermission = false
                    controller.cancel()
                }
                AgentimPairingEvent.Submit -> scope.launch {
                    if (permissionAdvisor.shouldRequestPermissionFor(state.homeserver.trim())) pendingPermission = true else controller.submit()
                }.let { }
                AgentimPairingEvent.RequestPermission -> permission.eventSink(
                    if (dialog == LocalNetworkPermissionDialog.Settings) {
                        PermissionsEvent.OpenSystemSettingAndCloseDialog
                    } else {
                        PermissionsEvent.ForceRequestPermissions
                    }
                )
                AgentimPairingEvent.DismissPermission -> {
                    pendingPermission = false
                }
                else -> controller.edit(event)
            }
        })
    }
}
