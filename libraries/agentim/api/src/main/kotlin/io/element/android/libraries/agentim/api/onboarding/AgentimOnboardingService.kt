/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.api.onboarding

enum class AgentimRegistrationPolicy { Open, Closed }

/** Non-secret metadata for resuming this installation's first registration. */
data class PendingAgentRegistration(val homeserver: String, val localpart: String, val displayName: String)

interface AgentimOnboardingService {
    suspend fun policy(homeserver: String): Result<AgentimRegistrationPolicy>
    suspend fun pending(): Result<PendingAgentRegistration?>
    suspend fun register(homeserver: String, localpart: String, displayName: String): Result<String>

    /** Install the app-owned native completion/recovery observer once per process. */
    fun start()
}
