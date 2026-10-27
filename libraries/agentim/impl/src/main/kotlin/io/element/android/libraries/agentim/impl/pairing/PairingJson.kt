/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.pairing

import io.element.android.libraries.agentim.api.pairing.AgentimPairingPhase
import io.element.android.libraries.agentim.api.pairing.AgentimPairingRequest
import org.json.JSONObject

internal object PairingJson {
    private val nonce = Regex("[A-Za-z0-9_-]{43}")
    private val code = Regex("[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}")
    private val fields = setOf(
        "pairing_id",
        "target_user_id",
        "user_code",
        "code_challenge",
        "device_name",
        "client_origin",
        "state",
        "created_at_ms",
        "expires_at_ms",
        "server_time_ms",
        "poll_interval_ms"
    )

    fun text(json: JSONObject, name: String, maximum: Int): String {
        val value = json.get(name)
        require(value is String && value.isNotEmpty() && value.codePointCount(0, value.length) <= maximum) { "Invalid pairing field" }
        require(value.none { it.code < 32 || it.code == 127 }) { "Invalid pairing text" }
        return value
    }

    private fun integer(json: JSONObject, name: String): Long {
        val value = json.get(name)
        require(value is Long || value is Int) { "Invalid pairing time" }
        return (value as Number).toLong().also { require(it in 0..9_007_199_254_740_991L) }
    }

    fun request(json: JSONObject): AgentimPairingRequest {
        require(json.keys().asSequence().toSet() == fields) { "Invalid pairing response fields" }
        val id = text(json, "pairing_id", 43).also { require(nonce.matches(it)) }
        val hash = text(json, "code_challenge", 43).also { require(nonce.matches(it)) }
        val userCode = text(json, "user_code", 9).also { require(code.matches(it)) }
        val target = text(json, "target_user_id", 255).also { require(Regex("@[^:\\s]+:[^\\s]+").matches(it)) }
        val origin = text(json, "client_origin", 255).also { require(it == "android-app://im.agentim.android") }
        val state = when (text(json, "state", 16)) {
            "pending" -> AgentimPairingPhase.Pending
            "approved" -> AgentimPairingPhase.Approved
            "rejected" -> AgentimPairingPhase.Rejected
            "cancelled" -> AgentimPairingPhase.Cancelled
            "consuming" -> AgentimPairingPhase.Consuming
            "consumed" -> AgentimPairingPhase.Consumed
            "expired" -> AgentimPairingPhase.Expired
            "failed" -> AgentimPairingPhase.Failed
            else -> error("Invalid pairing state")
        }
        val created = integer(json, "created_at_ms")
        val expires = integer(json, "expires_at_ms").also { require(it > created) }
        val serverTime = integer(json, "server_time_ms")
        val interval = integer(json, "poll_interval_ms").also { require(it == 2000L) }
        return AgentimPairingRequest(id, target, userCode, hash, text(json, "device_name", 80), origin, state, created, expires, serverTime, interval)
    }
}
