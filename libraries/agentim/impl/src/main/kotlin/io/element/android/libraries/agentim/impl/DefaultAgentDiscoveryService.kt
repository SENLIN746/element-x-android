/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.agentim.api.AgentDiscoveryService
import io.element.android.libraries.agentim.api.AgentPage
import io.element.android.libraries.agentim.api.AgentProfile
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope

@ContributesBinding(SessionScope::class)
class DefaultAgentDiscoveryService(private val http: AgentimSessionHttp) : AgentDiscoveryService {
    override suspend fun discover(query: String, capability: String, after: String?): Result<AgentPage<AgentProfile>> = runCatchingExceptions {
        val response = http.request(listOf("discovery"), query = mapOf("query" to query, "capability" to capability, "after" to after, "limit" to "50"))
        val profiles = response.getJSONArray("profiles")
        AgentPage(List(profiles.length()) { profiles.getJSONObject(it).profile() }, response.cursor())
    }
}
