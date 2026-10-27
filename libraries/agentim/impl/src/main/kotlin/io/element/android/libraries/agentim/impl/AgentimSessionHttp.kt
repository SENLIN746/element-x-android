/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl

import dev.zacsweers.metro.Inject
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.sessionstorage.api.SessionData
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import org.json.JSONObject

/** The current Android session supplies credentials; the SDK alone owns refresh. */
@Inject
class AgentimSessionHttp(
    private val matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    private val http: AgentimHttp,
) {
    private suspend fun session() = requireNotNull(sessionStore.getSession(matrixClient.sessionId.value)).also {
        check(it.isTokenValid) { "Session expired" }
    }

    private suspend fun refreshed(previous: SessionData): SessionData? {
        val updated = withTimeoutOrNull(5_000) {
            sessionStore.sessionsFlow().map { sessions -> sessions.firstOrNull { it.userId == previous.userId } }
                .first { it == null || !it.isTokenValid || it.accessToken != previous.accessToken }
        }
        return updated?.takeIf {
            it.isTokenValid && it.userId == previous.userId && it.deviceId == previous.deviceId &&
                it.homeserverUrl == previous.homeserverUrl && it.accessToken != previous.accessToken
        }
    }

    private suspend fun authenticated(
        method: String,
        body: JSONObject?,
        allowMissing: Boolean,
        url: (SessionData) -> HttpUrl,
    ): JSONObject {
        val original = session()
        val frozen = body?.let { JSONObject(it.toString()) }
        return try {
            http.request(url(original), original.accessToken, method, frozen, allowMissing)
        } catch (expired: AgentimAuthenticationExpired) {
            // Only explicit 401 is replayable: authorization precedes every supported write.
            val current = refreshed(original) ?: throw expired
            http.request(url(current), current.accessToken, method, frozen, allowMissing)
        }
    }

    suspend fun request(path: List<String>, method: String = "GET", body: JSONObject? = null, query: Map<String, String?> = emptyMap()): JSONObject =
        authenticated(method, body, false) { http.endpoint(it.homeserverUrl, path, query) }

    suspend fun ignoredUsers(method: String = "GET", body: JSONObject? = null): JSONObject =
        authenticated(method, body, method == "GET") { http.accountDataUrl(it.homeserverUrl, it.userId) }
}
