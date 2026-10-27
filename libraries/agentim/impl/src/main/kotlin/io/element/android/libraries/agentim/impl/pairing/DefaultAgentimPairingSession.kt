/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.pairing

import io.element.android.libraries.agentim.api.pairing.AgentimPairingGrant
import io.element.android.libraries.agentim.api.pairing.AgentimPairingRequest
import io.element.android.libraries.agentim.api.pairing.AgentimPairingSession
import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Single-use proof owner; credentials never appear in data-class diagnostics or public snapshots. */
internal class DefaultAgentimPairingSession(
    private val http: PairingHttp,
    private val homeserver: String,
    private val verifier: String,
    override val request: AgentimPairingRequest,
) : AgentimPairingSession {
    private val mutex = Mutex()
    private var consumed = false

    private suspend fun post(action: String): JSONObject = http.post(
        homeserver,
        listOf("pairings", request.pairingId, action),
        JSONObject().put("verifier", verifier)
    )

    override suspend fun status(): Result<AgentimPairingRequest> = runCatchingExceptions {
        PairingJson.request(post("status")).also {
            require(it.pairingId == request.pairingId && it.targetUserId == request.targetUserId &&
                it.codeChallenge == request.codeChallenge && it.userCode == request.userCode) { "Pairing request changed" }
        }
    }

    override suspend fun consume(): Result<AgentimPairingGrant> = runCatchingExceptions {
        mutex.withLock {
            check(!consumed) { "Start a new pairing request" }
            // Do not reissue a claim after cancellation, failed IO or an uncertain response.
            consumed = true
            val json = post("consume")
            require(json.keys().asSequence().toSet() == setOf("user_id", "login_token", "expires_in_ms"))
            require(json.get("expires_in_ms") == 60_000)
            val user = PairingJson.text(json, "user_id", 255)
            require(user == request.targetUserId) { "Pairing identity changed" }
            AgentimPairingGrant(user, PairingJson.text(json, "login_token", 4096))
        }
    }

    override suspend fun cancel(): Result<Unit> = runCatchingExceptions {
        post("cancel")
    }
}
