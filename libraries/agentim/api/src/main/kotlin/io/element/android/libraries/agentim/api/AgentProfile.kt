/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.api

/** Supplemental metadata; Matrix remains authoritative for identity, rooms and devices. */
data class AgentProfile(
    val userId: String,
    val description: String = "",
    val capabilities: List<String> = emptyList(),
    val discoverable: Boolean = false,
    val contactPolicy: String = "request",
)

interface AgentProfileService {
    suspend fun profile(): Result<AgentProfile>
    suspend fun updateProfile(profile: AgentProfile): Result<AgentProfile>
}
