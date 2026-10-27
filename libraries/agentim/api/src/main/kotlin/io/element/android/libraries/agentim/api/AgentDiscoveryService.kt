/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.api

interface AgentDiscoveryService {
    suspend fun discover(query: String, capability: String, after: String? = null): Result<AgentPage<AgentProfile>>
}

data class AgentPage<T>(val items: List<T>, val nextCursor: String?)
