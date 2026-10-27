/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.login

import dev.zacsweers.metro.Inject
import io.element.android.libraries.agentim.api.AgentimEnrollmentService
import io.element.android.libraries.agentim.api.pairing.AgentimPairingGrant
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.core.SessionId
import java.net.URI

internal data class AgentimLoginRequest(
    val homeserver: String,
    val token: String,
    val admission: String,
    val localpart: String,
    val displayName: String,
    val registering: Boolean,
) {
    override fun toString(): String = "AgentimLoginRequest(<redacted>)"
}

internal class AgentimHomeserverOriginChanged : IllegalArgumentException("Enter the actual homeserver URL directly")

/** Coordinates native device login while keeping enrollment and token exchange on the same validated server. */
@Inject
class AgentimDeviceLogin(
    private val authentication: MatrixAuthenticationService,
    private val enrollment: AgentimEnrollmentService,
) {
    internal suspend fun login(request: AgentimLoginRequest): Result<SessionId> = runCatchingExceptions {
        val serverUrl = prepare(request.homeserver).getOrThrow()
        val token = if (request.registering) {
            enrollment.register(serverUrl, request.admission, request.localpart, request.displayName).getOrThrow()
        } else {
            request.token
        }
        authentication.loginWithToken(token).getOrThrow()
    }

    internal suspend fun prepare(homeserver: String): Result<String> = runCatchingExceptions {
        val requestedOrigin = origin(homeserver)
        authentication.cancelOAuthLogin().getOrThrow()
        val server = authentication.setHomeserver(homeserver).getOrThrow()
        if (requestedOrigin != origin(server.url)) throw AgentimHomeserverOriginChanged()
        server.url
    }

    internal suspend fun connect(grant: AgentimPairingGrant): Result<SessionId> = runCatchingExceptions {
        grant.useOnce { authentication.loginWithToken(it).getOrThrow() }.also {
            require(it.value == grant.userId) { "Pairing identity changed" }
        }
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
