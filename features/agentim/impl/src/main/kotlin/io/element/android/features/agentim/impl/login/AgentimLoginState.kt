/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.login

import io.element.android.appconfig.AuthenticationConfig
import io.element.android.libraries.agentim.api.onboarding.AgentimRegistrationPolicy
import io.element.android.libraries.permissions.api.localnetwork.LocalNetworkPermissionDialog

data class AgentimLoginState(
    val homeserver: String = AuthenticationConfig.AGENTIM_HOMESERVER,
    val localpart: String = "",
    val displayName: String = "",
    val policy: AgentimRegistrationPolicy? = null,
    val policyLoading: Boolean = true,
    val resuming: Boolean = false,
    val busy: Boolean = false,
    val failed: Boolean = false,
    val nameUnavailable: Boolean = false,
    val permissionDialog: LocalNetworkPermissionDialog = LocalNetworkPermissionDialog.None,
    val eventSink: (AgentimLoginEvent) -> Unit = {},
)

sealed interface AgentimLoginEvent {
    data class Homeserver(val value: String) : AgentimLoginEvent
    data class Localpart(val value: String) : AgentimLoginEvent
    data class DisplayName(val value: String) : AgentimLoginEvent
    data object RefreshPolicy : AgentimLoginEvent
    data object RequestPermission : AgentimLoginEvent
    data object DismissPermission : AgentimLoginEvent
    data object Submit : AgentimLoginEvent
}
