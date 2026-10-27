/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.login

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.permissions.api.PermissionsEvent
import io.element.android.libraries.permissions.api.PermissionsPresenter
import io.element.android.libraries.permissions.api.localnetwork.LocalNetworkPermissionAdvisor
import io.element.android.libraries.permissions.api.localnetwork.LocalNetworkPermissionDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Inject
class AgentimLoginPresenter(
    private val deviceLogin: AgentimDeviceLogin,
    permissionsPresenterFactory: PermissionsPresenter.Factory,
    private val permissionAdvisor: LocalNetworkPermissionAdvisor,
) : Presenter<AgentimLoginState> {
    private val permissionsPresenter = permissionsPresenterFactory.create(Manifest.permission.ACCESS_LOCAL_NETWORK)

    @Composable
    override fun present(): AgentimLoginState {
        // Credentials deliberately remain in memory: never saveable state, preferences or logs.
        var state by remember { mutableStateOf(AgentimLoginState()) }
        val scope = rememberCoroutineScope()
        val permission = permissionsPresenter.present()
        var pendingPermission by remember { mutableStateOf(false) }
        fun submit() {
            if (state.busy) return
            val form = state
            state = state.copy(busy = true, failed = false)
            scope.launch {
                state = state.copy(token = "", admission = "")
                val result = deviceLogin.login(AgentimLoginRequest(
                    homeserver = form.homeserver.trim(),
                    token = form.token.trim(),
                    admission = form.admission,
                    localpart = form.localpart.trim(),
                    displayName = form.displayName.trim(),
                    registering = form.registering,
                ))
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                state = state.copy(
                    busy = false,
                    failed = result.isFailure,
                    originChanged = result.exceptionOrNull() is AgentimHomeserverOriginChanged,
                    token = "",
                    admission = ""
                )
            }
        }
        LaunchedEffect(permission.permissionGranted, pendingPermission) {
            if (permission.permissionGranted && pendingPermission) {
                pendingPermission = false
                submit()
            }
        }
        val dialog = when {
            !pendingPermission || permission.permissionGranted -> LocalNetworkPermissionDialog.None
            permission.shouldShowRationale || !permission.permissionAlreadyAsked -> LocalNetworkPermissionDialog.Rationale
            else -> LocalNetworkPermissionDialog.Settings
        }
        return state.copy(permissionDialog = dialog, eventSink = { event ->
            if (!state.busy) {
                state = when (event) {
                    is AgentimLoginEvent.Homeserver -> state.copy(homeserver = event.value)
                    is AgentimLoginEvent.Token -> state.copy(token = event.value)
                    is AgentimLoginEvent.Admission -> state.copy(admission = event.value)
                    is AgentimLoginEvent.Localpart -> state.copy(localpart = event.value)
                    is AgentimLoginEvent.DisplayName -> state.copy(displayName = event.value)
                    AgentimLoginEvent.ToggleRegistration -> state.copy(registering = !state.registering, failed = false, token = "", admission = "")
                    AgentimLoginEvent.RequestPermission -> {
                        permission.eventSink(if (dialog == LocalNetworkPermissionDialog.Settings) {
                            PermissionsEvent.OpenSystemSettingAndCloseDialog
                        } else {
                            PermissionsEvent.ForceRequestPermissions
                        })
                        state
                    }
                    AgentimLoginEvent.DismissPermission -> {
                        pendingPermission = false
                        state
                    }
                    AgentimLoginEvent.Submit -> {
                        scope.launch {
                            if (permissionAdvisor.shouldRequestPermissionFor(state.homeserver.trim())) pendingPermission = true else submit()
                        }
                        state
                    }
                }
            }
        })
    }
}
