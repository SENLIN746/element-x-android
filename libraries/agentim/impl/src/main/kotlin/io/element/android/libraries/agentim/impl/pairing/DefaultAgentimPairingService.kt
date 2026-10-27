/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.pairing

import android.util.Base64
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.agentim.api.pairing.AgentimPairingPhase
import io.element.android.libraries.agentim.api.pairing.AgentimPairingService
import io.element.android.libraries.agentim.api.pairing.AgentimPairingSession
import io.element.android.libraries.core.extensions.runCatchingExceptions
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom

@ContributesBinding(AppScope::class)
class DefaultAgentimPairingService(private val http: PairingHttp) : AgentimPairingService {
    override suspend fun start(homeserver: String, targetUserId: String, deviceName: String): Result<AgentimPairingSession> = runCatchingExceptions {
        val origin = URI(homeserver)
        require(origin.rawUserInfo == null && origin.rawQuery == null && origin.rawFragment == null && origin.path in listOf("", "/"))
        require(Regex("@[^:\\s]+:[^\\s]+").matches(targetUserId) && targetUserId.length <= 255)
        require(deviceName.isNotBlank() && deviceName.codePointCount(0, deviceName.length) <= 80 && deviceName.none { it.code < 32 || it.code == 127 })
        val verifier = encode(ByteArray(32).also { SecureRandom().nextBytes(it) })
        val challenge = encode(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val body = JSONObject()
            .put("target_user_id", targetUserId)
            .put("device_name", deviceName)
            .put("client_origin", "android-app://im.agentim.android")
            .put("code_challenge", challenge)
        val request = PairingJson.request(http.post(homeserver, listOf("pairings"), body))
        require(request.targetUserId == targetUserId && request.codeChallenge == challenge && request.deviceName == deviceName)
        require(request.state == AgentimPairingPhase.Pending)
        DefaultAgentimPairingSession(http, homeserver, verifier, request)
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
