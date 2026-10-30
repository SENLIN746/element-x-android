/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.account

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.agentim.api.account.AccountAccessRejected
import io.element.android.libraries.agentim.api.account.AccountBackupStatus
import io.element.android.libraries.sessionstorage.api.SessionData
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

@Inject
@SingleIn(AppScope::class)
class AccountKeyManager(private val vault: AccountVault, private val http: AccountHttp, private val sessions: SessionStore) {
    private val gate = Mutex()
    internal suspend fun session(user: String): SessionData = requireNotNull(sessions.getSession(user)).also { require(it.isTokenValid) }
    internal suspend fun prove(key: AccountCredential) {
        require(http.base(key.homeserver) == key.homeserver)
        validate(key, http.request(key.homeserver, "/_synapse/client/agentim/v2/account/credentials/verify", key.proof()))
    }
    private fun validate(key: AccountCredential, json: JSONObject) {
        require(json.getString("credential_id") == key.id && json.getString("user_id") == key.userId && !json.getBoolean("revoked")) {
            "Account proof did not match the selected identity"
        }
    }
    internal suspend fun ensure(user: String): AccountCredential = gate.withLock { ensureLocked(user) }
    private suspend fun ensureLocked(user: String): AccountCredential {
        val session = session(user)
        val home = http.base(session.homeserverUrl)
        val stored = vault.read(home, user)
        var key = stored?.let { AccountCredential.parse(it.getJSONObject("credential")) }
        if (key != null && stored?.optBoolean("ready") == true) {
            try {
                prove(key)
                return key
            } catch (rejected: AccountAccessRejected) {
                // Native session authority may replace an explicitly rejected old key.
                // Connectivity/proxy failures retain the original and never rotate silently.
                if (rejected.code != "M_FORBIDDEN") throw rejected
                key = null
            }
        }
        if (key == null) key = AccountCredential(home, user, randomAccountId(), randomAccountId())
        vault.save(key, false)
        val response = http.request(
            home,
            "/_synapse/client/agentim/v2/account/credentials",
            JSONObject().put("credential_id", key.id).put("code_challenge", key.challenge()),
            session.accessToken
        )
        validate(key, response)
        prove(key)
        vault.save(key, true)
        return key
    }
    internal fun status(key: AccountCredential): AccountBackupStatus = AccountBackupStatus(
        key.userId,
        key.homeserver,
        vault.read(key.homeserver, key.userId)?.optBoolean("exported") == true,
    )
}
