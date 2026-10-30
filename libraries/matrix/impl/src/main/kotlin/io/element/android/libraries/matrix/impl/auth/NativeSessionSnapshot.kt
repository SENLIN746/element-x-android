/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
package io.element.android.libraries.matrix.impl.auth

import io.element.android.libraries.androidutils.crypto.ClientSecret
import io.element.android.libraries.matrix.api.paths.SessionPaths
import io.element.android.libraries.matrix.impl.mapper.toSessionData
import io.element.android.libraries.sessionstorage.api.LoginType
import io.element.android.libraries.sessionstorage.api.SessionData
import org.matrix.rustcomponents.sdk.Client

/** The same native session mapping is used by password, token, OAuth and QR login. */
internal fun Client.snapshotSession(paths: SessionPaths, key: ClientSecret, type: LoginType): SessionData = session().toSessionData(
    isTokenValid = true,
    loginType = type,
    passphrase = key.formattedAsString(),
    sessionPaths = paths,
)
