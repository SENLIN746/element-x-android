/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.impl.auth

import io.element.android.libraries.androidutils.crypto.ClientSecret
import io.element.android.libraries.matrix.api.paths.SessionPaths
import io.element.android.libraries.matrix.impl.ClientBuilderSlidingSync
import io.element.android.libraries.matrix.impl.NoOpClientSessionDelegate
import io.element.android.libraries.matrix.impl.RustMatrixClientFactory
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.HumanQrLoginException
import org.matrix.rustcomponents.sdk.QrCodeData
import timber.log.Timber

internal class QrLoginClientFactory(private val rustMatrixClientFactory: RustMatrixClientFactory) {
    suspend fun create(sessionPaths: SessionPaths, qrCodeData: QrCodeData, key: ClientSecret, messageSearch: Boolean): Client {
        Timber.d("Creating client for QR Code login with simplified sliding sync")
        // The 2025 version of MSC4108 provides baseUrl; the 2024 version has null baseUrl and uses
        // serverName instead, which can be null or malformed. We only enforce presence/non-blankness
        // here and rely on serverNameOrHomeserverUrl()/the Rust builder layer to validate structure.
        val baseUrlOrServerName = qrCodeData.baseUrl() ?: qrCodeData.serverName()

        if (baseUrlOrServerName == null) {
            // With the 2024 version of MSC4108 we treat the absence of serverName as meaning that
            // the other device is not signed in.
            Timber.e("The QR code is from a device that is not yet signed in")
            throw HumanQrLoginException.OtherDeviceNotSignedIn()
        }

        if (baseUrlOrServerName.isBlank()) {
            Timber.e("The QR code contains an empty base URL or server name, which is invalid")
            throw HumanQrLoginException.Unknown()
        }

        return rustMatrixClientFactory
            .getBaseClientBuilder(
                sessionPaths = sessionPaths,
                clientSecret = key,
                slidingSyncType = ClientBuilderSlidingSync.Discovered,
                isMessageSearchAvailable = messageSearch,
                sessionDelegate = NoOpClientSessionDelegate,
            )
            .serverNameOrHomeserverUrl(baseUrlOrServerName)
            .build()
    }
}
