/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl

import dev.zacsweers.metro.Inject
import org.json.JSONObject

/** Project contact blocking to fresh native account data; callers serialize the entire consent/projection operation. */
@Inject
class MatrixIgnoreListProjection(private val http: AgentimSessionHttp) {
    suspend fun update(userId: String, blocked: Boolean) {
        val data = http.ignoredUsers()
        val ignored = if (data.has("ignored_users")) data.getJSONObject("ignored_users") else JSONObject()
        if (blocked) ignored.put(userId, JSONObject()) else ignored.remove(userId)
        http.ignoredUsers("PUT", data.put("ignored_users", ignored))
    }
}
