/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.agentim.api.AgentimIgnoreProjectionFailure
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
class ContactProjectionTest {
    private val policyUrl = "https://example.org/contacts".toHttpUrl()
    private val accountUrl = "https://example.org/account_data".toHttpUrl()
    private val sessionId = SessionId("@alice:example.org")
    private val target = "@bob:example.org"
    private val http = mockk<AgentimHttp>()
    private val store = mockk<SessionStore>()
    private val matrix = mockk<MatrixClient>()

    private fun service(): DefaultAgentContactService {
        every { matrix.sessionId } returns sessionId
        coEvery { store.getSession(sessionId.value) } returns aSessionData(sessionId = sessionId.value, isTokenValid = true)
            .copy(homeserverUrl = "https://example.org")
        every { http.endpoint(any(), any(), any()) } returns policyUrl
        every { http.accountDataUrl(any(), any()) } returns accountUrl
        coEvery { http.request(policyUrl, any(), "PUT", any(), false) } returns
            JSONObject().put("user_id", target).put("state", "none").put("blocked", true)
        val sessionHttp = AgentimSessionHttp(matrix, store, http)
        return DefaultAgentContactService(sessionHttp, MatrixIgnoreListProjection(sessionHttp))
    }

    @Test
    fun `rapid block then unblock reads fresh account data and preserves other users`() = runTest {
        val service = service()
        var data = JSONObject().put("ignored_users", JSONObject().put("@other:example.org", JSONObject()))
        val operations = mutableListOf<String>()
        coEvery { http.request(policyUrl, any(), "PUT", any(), false) } coAnswers {
            operations.add("POLICY " + arg<JSONObject>(3).getString("action"))
            delay(5)
            JSONObject().put("user_id", target).put("state", "none").put("blocked", true)
        }
        coEvery { http.request(accountUrl, any(), "GET", null, true) } coAnswers {
            operations.add("GET")
            delay(10)
            JSONObject(data.toString())
        }
        coEvery { http.request(accountUrl, any(), "PUT", any(), false) } coAnswers {
            operations.add("PUT")
            data = JSONObject(arg<JSONObject>(3).toString())
            JSONObject()
        }
        val block = async { service.contactAction(target, "block") }
        val unblock = async { service.contactAction(target, "unblock") }
        assertThat(block.await().isSuccess).isTrue()
        assertThat(unblock.await().isSuccess).isTrue()
        assertThat(data.getJSONObject("ignored_users").has(target)).isFalse()
        assertThat(data.getJSONObject("ignored_users").has("@other:example.org")).isTrue()
        assertThat(operations).containsExactly("POLICY block", "GET", "PUT", "POLICY unblock", "GET", "PUT").inOrder()
    }

    @Test
    fun `native projection failure is reported after contact policy succeeds`() = runTest {
        val service = service()
        coEvery { http.request(accountUrl, any(), "GET", null, true) } throws IOException("offline")
        val result = service.contactAction(target, "block")
        assertThat(result.exceptionOrNull()).isInstanceOf(AgentimIgnoreProjectionFailure::class.java)
    }
}
