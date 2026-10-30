/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
package io.element.android.libraries.pushproviders.unifiedpush

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.pushproviders.api.Distributor
import org.unifiedpush.android.connector.UnifiedPush

/** Android connector boundary; completion is owned by EndpointRegistrationHandler. */
fun interface UnifiedPushRegistration {
    suspend fun register(distributor: Distributor, clientSecret: String, sessionId: SessionId)
}

@ContributesBinding(AppScope::class)
class DefaultUnifiedPushRegistration(
    @ApplicationContext private val context: Context,
) : UnifiedPushRegistration {
    override suspend fun register(distributor: Distributor, clientSecret: String, sessionId: SessionId) {
        UnifiedPush.saveDistributor(context, distributor.value)
        UnifiedPush.register(context = context, instance = clientSecret, messageForDistributor = sessionId.value)
    }
}
