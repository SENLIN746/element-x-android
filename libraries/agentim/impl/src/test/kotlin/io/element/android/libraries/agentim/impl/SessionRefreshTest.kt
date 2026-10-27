/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SessionRefreshTest {
    private val id = SessionId("@alice:example.org")
    private val old = aSessionData(sessionId = id.value, isTokenValid = true)
        .copy(accessToken = "old-synthetic", refreshToken = "old-refresh", homeserverUrl = "https://example.org")
    private val fresh = old.copy(accessToken = "fresh-synthetic", refreshToken = "fresh-refresh")
    private val current = MutableStateFlow(listOf(old))
    private val http = mockk<AgentimHttp>()
    private val store = mockk<SessionStore>()
    private val matrix = mockk<MatrixClient>()
    private val url = "https://example.org/native".toHttpUrl()

    private fun service(): AgentimSessionHttp {
        every { matrix.sessionId } returns id
        coEvery { store.getSession(id.value) } coAnswers { current.value.firstOrNull { it.userId == id.value } }
        every { store.sessionsFlow() } returns current
        every { http.endpoint(any(), any(), any()) } returns url
        every { http.accountDataUrl(any(), any()) } returns url
        return AgentimSessionHttp(matrix, store, http)
    }

    @Test
    fun `401 waits for SDK credentials and repeats a frozen authorized intent once`() = runTest {
        val service = service()
        val body = JSONObject().put("action", "block")
        var first = ""
        var repeated = ""
        coEvery { http.request(url, old.accessToken, "PUT", any(), false) } coAnswers {
            first = arg<JSONObject>(3).toString()
            body.put("action", "remove")
            launch {
                delay(50)
                current.value = listOf(fresh)
            }
            throw AgentimAuthenticationExpired()
        }
        coEvery { http.request(url, fresh.accessToken, "PUT", any(), false) } coAnswers {
            repeated = arg<JSONObject>(3).toString()
            JSONObject().put("saved", true)
        }
        assertThat(service.request(listOf("contacts"), "PUT", body).getBoolean("saved")).isTrue()
        assertThat(repeated).isEqualTo(first)
        assertThat(JSONObject(repeated).getString("action")).isEqualTo("block")
        coVerify(exactly = 2) { http.request(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `native ignore reads use the same bounded credential refresh path`() = runTest {
        val service = service()
        coEvery { http.request(url, old.accessToken, "GET", null, true) } coAnswers {
            current.value = listOf(fresh)
            throw AgentimAuthenticationExpired()
        }
        coEvery { http.request(url, fresh.accessToken, "GET", null, true) } returns JSONObject().put("ignored_users", JSONObject())
        assertThat(service.ignoredUsers().has("ignored_users")).isTrue()
        coVerify(exactly = 2) { http.request(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `unchanged credentials time out without replay`() = runTest {
        val service = service()
        coEvery { http.request(url, old.accessToken, "GET", null, false) } throws AgentimAuthenticationExpired()
        val result = runCatching { service.request(listOf("profiles", "me")) }
        assertThat(result.exceptionOrNull()).isInstanceOf(AgentimAuthenticationExpired::class.java)
        coVerify(exactly = 1) { http.request(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `logout and changed user device or server never replay an earlier intent`() = runTest {
        val service = service()
        val invalid = listOf(
            emptyList(),
            listOf(fresh.copy(isTokenValid = false)),
            listOf(fresh.copy(userId = "@other:example.org")),
            listOf(fresh.copy(deviceId = "OTHER")),
            listOf(fresh.copy(homeserverUrl = "https://other.example.org")),
        )
        for (replacement in invalid) {
            clearMocks(http, answers = false)
            current.value = listOf(old)
            coEvery { http.request(url, old.accessToken, "PUT", any(), false) } coAnswers {
                current.value = replacement
                throw AgentimAuthenticationExpired()
            }
            val result = runCatching { service.request(listOf("contacts"), "PUT", JSONObject().put("action", "accept")) }
            assertThat(result.exceptionOrNull()).isInstanceOf(AgentimAuthenticationExpired::class.java)
            coVerify(exactly = 1) { http.request(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun `transport and non401 failures never replay a write`() = runTest {
        val service = service()
        coEvery { http.request(url, old.accessToken, "PUT", any(), false) } throws IOException("synthetic offline")
        val result = runCatching { service.request(listOf("contacts"), "PUT", JSONObject()) }
        assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
        coVerify(exactly = 1) { http.request(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a second 401 fails without another retry`() = runTest {
        val service = service()
        coEvery { http.request(url, old.accessToken, "GET", null, false) } coAnswers {
            current.value = listOf(fresh)
            throw AgentimAuthenticationExpired()
        }
        coEvery { http.request(url, fresh.accessToken, "GET", null, false) } throws AgentimAuthenticationExpired()
        val result = runCatching { service.request(listOf("profiles", "me")) }
        assertThat(result.exceptionOrNull()).isInstanceOf(AgentimAuthenticationExpired::class.java)
        coVerify(exactly = 2) { http.request(any(), any(), any(), any(), any()) }
    }
}
