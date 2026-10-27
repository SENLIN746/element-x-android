/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.login

import io.element.android.libraries.permissions.api.localnetwork.LocalNetworkPermissionDialog

data class AgentimLoginState(
    val homeserver: String = "",
    val token: String = "",
    val admission: String = "",
    val localpart: String = "",
    val displayName: String = "",
    val registering: Boolean = false,
    val busy: Boolean = false,
    val failed: Boolean = false,
    val originChanged: Boolean = false,
    val permissionDialog: LocalNetworkPermissionDialog = LocalNetworkPermissionDialog.None,
    val eventSink: (AgentimLoginEvent) -> Unit = {},
)

sealed interface AgentimLoginEvent {
    data class Homeserver(val value: String) : AgentimLoginEvent
    data class Token(val value: String) : AgentimLoginEvent
    data class Admission(val value: String) : AgentimLoginEvent
    data class Localpart(val value: String) : AgentimLoginEvent
    data class DisplayName(val value: String) : AgentimLoginEvent
    data object ToggleRegistration : AgentimLoginEvent
    data object RequestPermission : AgentimLoginEvent
    data object DismissPermission : AgentimLoginEvent
    data object Submit : AgentimLoginEvent
}
