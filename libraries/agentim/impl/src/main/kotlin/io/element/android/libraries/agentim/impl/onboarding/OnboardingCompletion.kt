/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.onboarding

import dev.zacsweers.metro.Inject
import io.element.android.libraries.agentim.impl.account.AccountKeyManager
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

/** Completes only after the published native device is syncing and its own encryption keys are uploaded. */
@Inject
class OnboardingCompletion(
    private val attempts: OnboardingAttempts,
    private val sessions: SessionStore,
    private val clients: MatrixClientProvider,
    private val http: OnboardingHttp,
    private val accountKeys: AccountKeyManager,
) {
    internal suspend fun observe() {
        combine(sessions.sessionsFlow(), attempts.changes) { all, _ -> all }.collectLatest { all ->
            val attempt = attempts.pending() ?: return@collectLatest
            val session = all.firstOrNull { it.userId == attempt.userId } ?: return@collectLatest
            while (attempts.pending()?.attemptId == attempt.attemptId) {
                if (runCatchingExceptions { complete(attempt, session) }.getOrDefault(false)) {
                    attempts.finish(attempt)
                    break
                }
                delay(10_000)
            }
        }
    }

    internal suspend fun complete(attempt: OnboardingAttempt, session: SessionData): Boolean {
        require(session.isTokenValid && session.deviceId == attempt.deviceId && session.userId == attempt.userId)
        require(http.base(session.homeserverUrl) == attempt.homeserver)
        val client = clients.getOrRestore(SessionId(session.userId)).getOrThrow()
        require(client.deviceId.value == attempt.deviceId)
        if (client.syncService.syncState.value != SyncState.Running) return false
        val query = JSONObject().put("device_keys", JSONObject().put(session.userId, JSONArray().put(attempt.deviceId)))
        val keys = http.request(attempt.homeserver, "/_matrix/client/v3/keys/query", query, session.accessToken)
            .getJSONObject("device_keys").optJSONObject(session.userId)?.optJSONObject(attempt.deviceId) ?: return false
        require(keys.getString("user_id") == session.userId && keys.getString("device_id") == attempt.deviceId)
        val publicKeys = keys.getJSONObject("keys")
        if (listOf("ed25519", "curve25519").any { publicKeys.optString("$it:${attempt.deviceId}").isBlank() }) return false
        accountKeys.ensure(session.userId)
        val response = http.request(
            attempt.homeserver,
            "/_synapse/client/agentim/v2/registration/${attempt.attemptId}/complete",
            JSONObject(),
            session.accessToken,
        )
        require(response.keys().asSequence().toSet() == setOf("completed") && response.get("completed") == true)
        return true
    }
}
