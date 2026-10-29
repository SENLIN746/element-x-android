/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.pairing

import io.element.android.appconfig.AuthenticationConfig
import io.element.android.libraries.agentim.api.pairing.AgentimPairingRequest
import io.element.android.libraries.permissions.api.localnetwork.LocalNetworkPermissionDialog

data class AgentimPairingState(
    val homeserver: String = AuthenticationConfig.AGENTIM_HOMESERVER,
    val targetUserId: String = "",
    val deviceName: String = "AgentIM Android",
    val phase: PairingScreenPhase = PairingScreenPhase.Idle,
    val request: AgentimPairingRequest? = null,
    val seconds: Long = 0,
    val detailsVisible: Boolean = false,
    val permissionDialog: LocalNetworkPermissionDialog = LocalNetworkPermissionDialog.None,
    val eventSink: (AgentimPairingEvent) -> Unit = {},
) {
    val busy: Boolean get() = phase in
        setOf(PairingScreenPhase.Creating, PairingScreenPhase.Waiting, PairingScreenPhase.Connecting, PairingScreenPhase.Connected)
}

enum class PairingScreenPhase { Idle, Creating, Waiting, Connecting, Connected, Rejected, Expired, Failed }

sealed interface AgentimPairingEvent {
    data class Homeserver(val value: String) : AgentimPairingEvent
    data class Target(val value: String) : AgentimPairingEvent
    data class DeviceName(val value: String) : AgentimPairingEvent
    data object ToggleDetails : AgentimPairingEvent
    data object CopyFingerprint : AgentimPairingEvent
    data object Submit : AgentimPairingEvent
    data object Cancel : AgentimPairingEvent
    data object RequestPermission : AgentimPairingEvent
    data object DismissPermission : AgentimPairingEvent
}
