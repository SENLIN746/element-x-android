/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.impl.auth.bootstrap

import dev.zacsweers.metro.Inject
import io.element.android.libraries.androidutils.crypto.ClientSecret
import io.element.android.libraries.matrix.api.paths.SessionPaths
import io.element.android.libraries.matrix.impl.auth.MatrixTokenLogin
import io.element.android.libraries.matrix.impl.mapper.toSessionData
import io.element.android.libraries.sessionstorage.api.LoginType
import io.element.android.libraries.sessionstorage.api.SessionData
import io.element.android.libraries.sessionstorage.api.SessionStore
import org.matrix.rustcomponents.sdk.Client

/** Journal the self-created token session, then let the normal native finalizer restore the exact store. */
@Inject
class MatrixBootstrapLogin(
    private val journal: BootstrapSessionJournal,
    private val sessions: SessionStore,
    private val tokenLogin: MatrixTokenLogin,
) {
    suspend fun existing(homeserver: String, userId: String, deviceId: String): SessionData? {
        val session = sessions.getSession(userId) ?: journal.read() ?: return null
        require(
            session.isTokenValid && session.userId == userId && session.deviceId == deviceId &&
            session.homeserverUrl.trimEnd('/') == homeserver.trimEnd('/')
        ) { "Pending native identity changed" }
        return session
    }

    suspend fun isPublished(userId: String): Boolean = sessions.getSession(userId) != null

    suspend fun create(client: Client, token: String, userId: String, deviceId: String, paths: SessionPaths, key: ClientSecret): SessionData {
        require(journal.read() == null) { "Restore the pending native session first" }
        val session = tokenLogin.exchange(client, token, deviceId)
        require(session.userId == userId && session.deviceId == deviceId) { "Initial identity authorization changed" }
        return session.toSessionData(
            isTokenValid = true,
            loginType = LoginType.SSO,
            passphrase = key.formattedAsString(),
            sessionPaths = paths,
        ).also(journal::write)
    }

    fun finish(userId: String, deviceId: String) = journal.finish(userId, deviceId)
}
