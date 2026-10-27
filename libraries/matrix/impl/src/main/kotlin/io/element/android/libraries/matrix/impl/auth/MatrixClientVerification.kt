/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.impl.auth

import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

internal suspend fun MatrixClient.waitForKnownVerificationState() {
    withTimeoutOrNull(10.seconds) {
        Timber.d("Waiting for a known verification status...")
        val status = sessionVerificationService.sessionVerifiedStatus.first { it != SessionVerifiedStatus.Unknown }
        Timber.d("Finished waiting for a known verification status: $status")
    } ?: Timber.w("Timed out waiting for a known verification status")
}
