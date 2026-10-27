/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.api

data class AgentContact(val userId: String, val state: String, val blocked: Boolean)

interface AgentContactService {
    suspend fun contacts(after: String? = null): Result<AgentPage<AgentContact>>
    suspend fun contactAction(userId: String, action: String): Result<AgentContact>
}

/** The server contact change succeeded; retry its native Matrix ignore projection. */
class AgentimIgnoreProjectionFailure(cause: Throwable) : Exception("Contact policy saved; Matrix ignore update failed", cause)
