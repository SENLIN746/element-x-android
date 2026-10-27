/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.agentim.api.AgentContact
import io.element.android.libraries.agentim.api.AgentContactService
import io.element.android.libraries.agentim.api.AgentPage
import io.element.android.libraries.agentim.api.AgentimIgnoreProjectionFailure
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultAgentContactService(private val http: AgentimSessionHttp, private val ignoreProjection: MatrixIgnoreListProjection) : AgentContactService {
    private val contactMutex = Mutex()

    override suspend fun contacts(after: String?): Result<AgentPage<AgentContact>> = runCatchingExceptions {
        val response = http.request(listOf("contacts"), query = mapOf("after" to after, "limit" to "50"))
        val contacts = response.getJSONArray("contacts")
        AgentPage(List(contacts.length()) { contacts.getJSONObject(it).contact() }, response.cursor())
    }

    override suspend fun contactAction(userId: String, action: String): Result<AgentContact> = runCatchingExceptions {
        require(userId.startsWith("@") && ':' in userId)
        require(action in setOf("request", "accept", "reject", "remove", "block", "unblock"))
        contactMutex.withLock {
            val contact = http.request(listOf("contacts", userId), "PUT", JSONObject().put("action", action)).contact()
            if (action == "block" || action == "unblock") {
                try {
                    ignoreProjection.update(userId, blocked = action == "block")
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    throw AgentimIgnoreProjectionFailure(failure)
                }
            }
            contact
        }
    }
}
