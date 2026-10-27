/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.navigation

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.zacsweers.metro.Inject
import io.element.android.libraries.deeplink.api.DeepLinkCreator
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.UserId

/** Delegates encrypted room creation and all chat UI to the existing Matrix SDK and app navigation. */
@Inject
class AgentimRoomNavigator(
    private val matrixClient: MatrixClient,
    private val deepLinkCreator: DeepLinkCreator,
    @ApplicationContext private val context: Context,
) {
    suspend fun open(userId: String) {
        val user = UserId(userId)
        val existing = matrixClient.findDM(user).getOrThrow()
        val encryptedExisting = existing?.takeIf { matrixClient.getRoomInfo(it).getOrThrow()?.isEncrypted == true }
        val roomId = encryptedExisting ?: matrixClient.createDM(user, isEncrypted = true).getOrThrow()
        val intent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        intent.action = Intent.ACTION_VIEW
        intent.data = deepLinkCreator.create(matrixClient.sessionId, roomId, null, null).toUri()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        context.startActivity(intent)
    }
}
