/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.onboarding

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.core.DeviceId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.sync.SyncState
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OnboardingCompletionTest {
    @Test
    fun `completion requires native sync and the bound device public keys before confirming`() = runTest {
        val attempt = OnboardingAttempt("https://example", "agent", "", "a".repeat(43), "device", "v".repeat(43), "@agent:example")
        val session = aSessionData(sessionId = "@agent:example", deviceId = "device", isTokenValid = true).copy(homeserverUrl = "https://example")
        val provider = mockk<MatrixClientProvider>()
        val client = mockk<MatrixClient>()
        val http = mockk<OnboardingHttp>()
        val sync = MutableStateFlow(SyncState.Offline)
        every { client.deviceId } returns DeviceId("device")
        every { client.syncService.syncState } returns sync
        every { http.base("https://example") } returns "https://example"
        coEvery { provider.getOrRestore(SessionId("@agent:example")) } returns Result.success(client)
        val service = OnboardingCompletion(mockk(), mockk(), provider, http)
        assertThat(service.complete(attempt, session)).isFalse()
        coVerify(exactly = 0) { http.request(any(), any(), any(), any()) }
        sync.value = SyncState.Running
        val device = JSONObject().put("user_id", "@agent:example").put("device_id", "device").put("keys", JSONObject())
        coEvery { http.request(any(), "/_matrix/client/v3/keys/query", any(), any()) } returns
            JSONObject().put("device_keys", JSONObject().put("@agent:example", JSONObject().put("device", device)))
        assertThat(service.complete(attempt, session)).isFalse()
        coVerify(exactly = 0) { http.request(any(), match { it.endsWith("/complete") }, any(), any()) }
        device.getJSONObject("keys").put("ed25519:device", "synthetic-public-signing").put("curve25519:device", "synthetic-public-encryption")
        coEvery { http.request(any(), match { it.endsWith("/complete") }, any(), any()) } returns JSONObject().put("completed", true)
        assertThat(service.complete(attempt, session)).isTrue()
        every { client.deviceId } returns DeviceId("foreign")
        assertThat(runCatching { service.complete(attempt, session) }.isFailure).isTrue()
        coVerify(exactly = 1) { http.request(any(), match { it.endsWith("/complete") }, any(), any()) }
    }
}
