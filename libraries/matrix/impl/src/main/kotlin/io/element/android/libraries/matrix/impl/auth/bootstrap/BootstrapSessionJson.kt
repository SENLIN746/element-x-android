/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.impl.auth.bootstrap

import io.element.android.libraries.sessionstorage.api.LoginType
import io.element.android.libraries.sessionstorage.api.SessionData
import org.json.JSONObject
import java.util.Date

/** Private serialization only; never used by UI, Intents or diagnostic logging. */
internal object BootstrapSessionJson {
    fun encode(session: SessionData): ByteArray = JSONObject()
        .put("user", session.userId)
        .put("device", session.deviceId)
        .put("access", session.accessToken)
        .put("refresh", session.refreshToken)
        .put("homeserver", session.homeserverUrl)
        .put("passphrase", session.passphrase)
        .put("directory", session.sessionPath)
        .put("cache", session.cachePath)
        .toString().toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): SessionData {
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        return SessionData(
            userId = json.getString("user"),
            deviceId = json.getString("device"),
            accessToken = json.getString("access"),
            refreshToken = json.optString("refresh").takeIf { it.isNotEmpty() },
            homeserverUrl = json.getString("homeserver"),
            passphrase = json.getString("passphrase"),
            sessionPath = json.getString("directory"),
            cachePath = json.getString("cache"),
            oAuthData = null,
            loginTimestamp = Date(),
            isTokenValid = true,
            loginType = LoginType.SSO,
            position = 0,
            lastUsageIndex = 0,
            userDisplayName = null,
            userAvatarUrl = null,
            userAvatarData = null,
        )
    }
}
