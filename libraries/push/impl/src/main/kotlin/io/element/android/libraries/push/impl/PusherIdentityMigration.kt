/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
package io.element.android.libraries.push.impl

import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.pusher.UnsetHttpPusherData

/** After installing our pusher, retire only the old identity for this exact device endpoint. */
internal suspend fun migrateAgentIMPusherIdentity(matrixClient: MatrixClient, appId: String, pushKey: String): Result<Unit> {
    if (appId != "im.agentim.android") return Result.success(Unit)
    return matrixClient.pushersService.unsetHttpPusher(
        UnsetHttpPusherData(
            pushKey = pushKey,
            appId = "im.vector.app.android",
        )
    )
}
