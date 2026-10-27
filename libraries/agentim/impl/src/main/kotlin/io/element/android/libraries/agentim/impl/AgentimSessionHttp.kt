/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl

import dev.zacsweers.metro.Inject
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.sessionstorage.api.SessionStore
import org.json.JSONObject

/** The current Android session supplies its own credentials exclusively within the data layer. */
@Inject
class AgentimSessionHttp(
    private val matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    private val http: AgentimHttp,
) {
    private suspend fun session() = requireNotNull(sessionStore.getSession(matrixClient.sessionId.value)).also {
        check(it.isTokenValid) { "Session expired" }
    }

    suspend fun request(path: List<String>, method: String = "GET", body: JSONObject? = null, query: Map<String, String?> = emptyMap()): JSONObject {
        val session = session()
        return http.request(http.endpoint(session.homeserverUrl, path, query), session.accessToken, method, body)
    }

    suspend fun ignoredUsers(method: String = "GET", body: JSONObject? = null): JSONObject {
        val session = session()
        return http.request(http.accountDataUrl(session.homeserverUrl, session.userId), session.accessToken, method, body, allowMissing = method == "GET")
    }
}
