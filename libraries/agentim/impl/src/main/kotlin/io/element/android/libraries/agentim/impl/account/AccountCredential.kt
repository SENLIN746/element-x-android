/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.account

import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

internal fun randomAccountId(): String = accountEncode(ByteArray(32).also { SecureRandom().nextBytes(it) })
private fun accountEncode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

/** Never render this object in state, diagnostics, Intents or logs. */
internal class AccountCredential(val homeserver: String, val userId: String, val id: String, val secret: String) {
    fun proof(): JSONObject = JSONObject().put("credential_id", id).put("credential_secret", secret)
    fun challenge(): String = accountEncode(MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.US_ASCII)))
    fun json(): JSONObject = proof().put("version", 1).put("kind", "agentim-account-credential")
        .put("homeserver", homeserver).put("user_id", userId)
    override fun toString(): String = "AccountCredential(<private>)"

    companion object {
        fun parse(json: JSONObject): AccountCredential {
            require(json.keys().asSequence().toSet() == setOf("version", "kind", "homeserver", "user_id", "credential_id", "credential_secret"))
            require(json.get("version") == 1 && json.getString("kind") == "agentim-account-credential")
            require(listOf("kind", "homeserver", "user_id", "credential_id", "credential_secret").all { json.get(it) is String })
            val id = json.getString("credential_id")
            val secret = json.getString("credential_secret")
            require(listOf(id, secret).all { Regex("[A-Za-z0-9_-]{43}").matches(it) })
            val user = json.getString("user_id")
            require(user.matches(Regex("@[^:]+:.+")) && user.length <= 255)
            return AccountCredential(json.getString("homeserver"), user, id, secret)
        }
    }
}
