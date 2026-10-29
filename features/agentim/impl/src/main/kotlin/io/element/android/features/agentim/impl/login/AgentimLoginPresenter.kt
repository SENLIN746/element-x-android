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
import io.element.android.libraries.agentim.api.onboarding.AgentimOnboardingService
import io.element.android.libraries.agentim.api.onboarding.AgentimRegistrationRejected
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
    private val onboarding: AgentimOnboardingService,
    permissionsPresenterFactory: PermissionsPresenter.Factory,
    private val permissionAdvisor: LocalNetworkPermissionAdvisor,
) : Presenter<AgentimLoginState> {
    private val permissionsPresenter = permissionsPresenterFactory.create(Manifest.permission.ACCESS_LOCAL_NETWORK)

    @Composable
    override fun present(): AgentimLoginState {
        // UI contains public metadata only. The data layer owns the durable private proof.
        var state by remember { mutableStateOf(AgentimLoginState()) }
        val scope = rememberCoroutineScope()
        var refresh by remember { mutableStateOf(0) }
        var restored by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            onboarding.pending().getOrNull()?.let { pending ->
                state = state.copy(homeserver = pending.homeserver, localpart = pending.localpart, displayName = pending.displayName, resuming = true)
            }
            restored = true
        }
        LaunchedEffect(restored, state.homeserver, refresh) {
            if (restored) {
                state = state.copy(policy = null, policyLoading = true)
                val policy = onboarding.policy(state.homeserver.trim()).getOrNull()
                state = state.copy(policy = policy, policyLoading = false)
            }
        }
        val permission = permissionsPresenter.present()
        var pendingPermission by remember { mutableStateOf(false) }
        fun submit() {
            if (state.busy) return
            val form = state
            state = state.copy(busy = true, failed = false, nameUnavailable = false)
            scope.launch {
                val result = deviceLogin.register(form.homeserver.trim(), form.localpart.trim(), form.displayName.trim())
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                if (result.isFailure) {
                    state = state.copy(resuming = false)
                    onboarding.pending().getOrNull()?.let { pending ->
                        state = state.copy(homeserver = pending.homeserver, localpart = pending.localpart, displayName = pending.displayName, resuming = true)
                    }
                }
                state = state.copy(
                    busy = false,
                    failed = result.isFailure,
                    nameUnavailable = (result.exceptionOrNull() as? AgentimRegistrationRejected)?.code == "M_USER_IN_USE",
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
                    is AgentimLoginEvent.Homeserver -> state.copy(homeserver = event.value, policy = null, policyLoading = true)
                    is AgentimLoginEvent.Localpart -> state.copy(localpart = event.value)
                    is AgentimLoginEvent.DisplayName -> state.copy(displayName = event.value)
                    AgentimLoginEvent.RefreshPolicy -> {
                        refresh += 1
                        state
                    }
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
