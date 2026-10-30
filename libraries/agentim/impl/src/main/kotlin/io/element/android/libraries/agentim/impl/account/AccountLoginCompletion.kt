/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.account

import dev.zacsweers.metro.Inject
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.sync.SyncState
import io.element.android.libraries.sessionstorage.api.SessionData
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import org.json.JSONObject

@Inject
class AccountLoginCompletion(private val vault: AccountVault, private val sessions: SessionStore,
    private val clients: MatrixClientProvider, private val http: AccountHttp) {
    internal suspend fun observe() {
        combine(sessions.sessionsFlow(), vault.changes) { all, _ -> all }.collectLatest { all ->
            val pending = vault.pending() ?: return@collectLatest
            val session = all.firstOrNull { it.userId == pending.getString("user") } ?: return@collectLatest
            val attempt = pending.getString("attempt")
            while (vault.pending()?.getString("attempt") == attempt) {
                if (runCatchingExceptions { complete(pending, session) }.getOrDefault(false)) {
                    vault.finish(attempt)
                    break
                }
                delay(10_000)
            }
        }
    }
    internal suspend fun complete(pending: JSONObject, session: SessionData): Boolean {
        val device = pending.getString("device")
        require(session.isTokenValid && session.deviceId == device && session.userId == pending.getString("user"))
        val home = pending.getString("home")
        require(http.base(session.homeserverUrl) == home)
        val client = clients.getOrRestore(SessionId(session.userId)).getOrThrow()
        require(client.deviceId.value == device)
        if (client.syncService.syncState.value != SyncState.Running) return false
        val query = JSONObject().put("device_keys", JSONObject().put(session.userId, JSONArray().put(device)))
        val keys = http.request(home, "/_matrix/client/v3/keys/query", query, session.accessToken)
            .getJSONObject("device_keys").optJSONObject(session.userId)?.optJSONObject(device) ?: return false
        require(keys.getString("user_id") == session.userId && keys.getString("device_id") == device)
        val publicKeys = keys.getJSONObject("keys")
        if (listOf("ed25519", "curve25519").any { publicKeys.optString("$it:$device").isBlank() }) return false
        val response = http.request(
            home,
            "/_synapse/client/agentim/v2/account/logins/${pending.getString("attempt")}/complete",
            JSONObject(),
            session.accessToken
        )
        require(response.getBoolean("ok"))
        return true
    }
}
