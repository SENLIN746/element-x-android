/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.pushproviders.unifiedpush

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.pushproviders.api.Distributor
import io.element.android.libraries.pushproviders.unifiedpush.registration.EndpointRegistrationHandler
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.seconds

interface RegisterUnifiedPushUseCase {
    suspend fun execute(distributor: Distributor, clientSecret: String, sessionId: SessionId): Result<Unit>
}

@ContributesBinding(AppScope::class)
class DefaultRegisterUnifiedPushUseCase(
    private val registration: UnifiedPushRegistration,
    private val endpointRegistrationHandler: EndpointRegistrationHandler,
) : RegisterUnifiedPushUseCase {
    override suspend fun execute(distributor: Distributor, clientSecret: String, sessionId: SessionId): Result<Unit> {
        return runCatchingExceptions {
            withTimeoutOrNull(30.seconds) {
                coroutineScope {
                    // Subscribe before the connector can synchronously report a new endpoint.
                    val completion = async(start = CoroutineStart.UNDISPATCHED) {
                        endpointRegistrationHandler.state
                            .filter { it.clientSecret == clientSecret }
                            .first().result.getOrThrow()
                    }
                    try {
                        registration.register(distributor, clientSecret, sessionId)
                        completion.await()
                    } finally {
                        completion.cancel()
                    }
                }
            } ?: throw TimeoutException("UnifiedPush registration did not complete")
        }
    }
}
