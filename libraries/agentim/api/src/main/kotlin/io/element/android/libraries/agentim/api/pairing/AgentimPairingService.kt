/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.api.pairing

import java.util.concurrent.atomic.AtomicBoolean

/** The visual client requests a device; only the authenticated Agent can authorize it. */
interface AgentimPairingService {
    suspend fun start(homeserver: String, targetUserId: String, deviceName: String): Result<AgentimPairingSession>
}

/** An in-memory proof owner. Do not persist, log or expose this object as UI state. */
interface AgentimPairingSession {
    val request: AgentimPairingRequest
    suspend fun status(): Result<AgentimPairingRequest>
    suspend fun consume(): Result<AgentimPairingGrant>
    suspend fun cancel(): Result<Unit>
}

/** A short grant is passed straight to native authentication, never to the UI snapshot. */
class AgentimPairingGrant(val userId: String, private val loginToken: String) {
    private val used = AtomicBoolean(false)

    /** A failed or interrupted native login cannot replay the same credential handoff. */
    suspend fun <T> useOnce(login: suspend (String) -> T): T {
        check(used.compareAndSet(false, true)) { "Start a new pairing request" }
        return login(loginToken)
    }

    override fun toString(): String = "AgentimPairingGrant(<redacted>)"
}
