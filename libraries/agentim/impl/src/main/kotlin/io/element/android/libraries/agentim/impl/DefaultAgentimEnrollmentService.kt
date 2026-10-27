/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.agentim.api.AgentimEnrollmentService
import io.element.android.libraries.core.extensions.runCatchingExceptions
import org.json.JSONObject

@ContributesBinding(AppScope::class)
class DefaultAgentimEnrollmentService(private val http: AgentimHttp) : AgentimEnrollmentService {
    override suspend fun register(homeserver: String, admission: String, localpart: String, displayName: String): Result<String> =
        runCatchingExceptions {
            require(localpart.matches(Regex("[a-z0-9][a-z0-9._=-]{0,63}"))) { "Use a lowercase Agent localpart" }
            val body = JSONObject().put("localpart", localpart)
            if (displayName.isNotBlank()) body.put("display_name", displayName)
            val response = http.request(http.endpoint(homeserver, listOf("registration")), admission, "POST", body)
            response.getString("login_token")
        }
}
