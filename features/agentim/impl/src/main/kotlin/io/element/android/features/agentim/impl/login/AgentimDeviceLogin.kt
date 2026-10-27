/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.login

import dev.zacsweers.metro.Inject
import io.element.android.libraries.agentim.api.AgentimEnrollmentService
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.core.SessionId
import java.net.URI

internal class AgentimLoginRequest(
    val homeserver: String,
    val token: String,
    val admission: String,
    val localpart: String,
    val displayName: String,
    val registering: Boolean,
)

internal class AgentimHomeserverOriginChanged : IllegalArgumentException("Enter the actual homeserver URL directly")

/** Coordinates native device login while keeping enrollment and token exchange on the same validated server. */
@Inject
class AgentimDeviceLogin(
    private val authentication: MatrixAuthenticationService,
    private val enrollment: AgentimEnrollmentService,
) {
    internal suspend fun login(request: AgentimLoginRequest): Result<SessionId> = runCatchingExceptions {
        val requestedOrigin = origin(request.homeserver)
        authentication.cancelOAuthLogin().getOrThrow()
        val server = authentication.setHomeserver(request.homeserver).getOrThrow()
        if (requestedOrigin != origin(server.url)) throw AgentimHomeserverOriginChanged()
        val token = if (request.registering) {
            enrollment.register(server.url, request.admission, request.localpart, request.displayName).getOrThrow()
        } else {
            request.token
        }
        authentication.loginWithToken(token).getOrThrow()
    }

    private fun origin(url: String): Triple<String, String, Int> {
        val uri = URI(url)
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        val scheme = requireNotNull(uri.scheme).lowercase()
        require(scheme == "https" || scheme == "http")
        val host = requireNotNull(uri.host).lowercase()
        return Triple(scheme, host, if (uri.port != -1) {
            uri.port
        } else if (scheme == "https") {
            443
        } else {
            80
        })
    }
}
