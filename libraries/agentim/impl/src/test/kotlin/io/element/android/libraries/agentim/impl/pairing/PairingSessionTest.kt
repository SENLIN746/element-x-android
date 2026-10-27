/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.pairing

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.agentim.api.pairing.AgentimPairingGrant
import io.element.android.libraries.agentim.api.pairing.AgentimPairingPhase
import io.element.android.libraries.agentim.api.pairing.AgentimPairingRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PairingSessionTest {
    private val http = mockk<PairingHttp>()
    private val request = AgentimPairingRequest(
        "p".repeat(43),
        "@agent:example",
        "ABCD-EFGH",
        "c".repeat(43),
        "Android",
        "android-app://im.agentim.android",
        AgentimPairingPhase.Pending,
        1_000,
        301_000,
        1_000,
        2_000
    )
    private val verifier = "v".repeat(43)
    private fun session() = DefaultAgentimPairingSession(http, "https://agents.example", verifier, request)
    private fun json() = JSONObject()
        .put("pairing_id", request.pairingId)
        .put("target_user_id", request.targetUserId)
        .put("user_code", request.userCode)
        .put("code_challenge", request.codeChallenge)
        .put("device_name", "Android")
        .put("client_origin", request.clientOrigin)
        .put("state", "approved")
        .put("created_at_ms", 1_000)
        .put("expires_at_ms", 301_000)
        .put("server_time_ms", 1_001)
        .put("poll_interval_ms", 2_000)

    @Test
    fun `proof is only in POST body and absent from snapshot or diagnostics`() = runTest {
        val subject = session()
        coEvery { http.post(any(), any(), any()) } coAnswers {
            assertThat(arg<List<String>>(1)).isEqualTo(listOf("pairings", request.pairingId, "status"))
            assertThat(arg<JSONObject>(2).getString("verifier")).isEqualTo(verifier)
            json()
        }
        assertThat(subject.status().getOrThrow().state).isEqualTo(AgentimPairingPhase.Approved)
        assertThat(subject.toString()).doesNotContain(verifier)
        assertThat(subject.request.toString()).doesNotContain(verifier)
        assertThat(JSONObject(subject.request.fingerprint()).getString("pairing_id")).isEqualTo(request.pairingId)
        assertThat(AgentimPairingGrant(request.targetUserId, "synthetic-private-grant").toString()).doesNotContain("synthetic-private-grant")
    }

    @Test
    fun `malformed times unknown states fields and fingerprints fail closed`() = runTest {
        for (value in listOf(
                json()
        .put("poll_interval_ms", 1),
                json()
        .put("server_time_ms", 1.5),
                json()
        .put("state", "unknown"),
            json()
        .put("owner", "@other:example"),
                json()
        .put("code_challenge", "x".repeat(43)),
                json()
        .put("client_origin", "android-app://untrusted")
            )) {
            coEvery { http.post(any(), any(), any()) } returns value
            assertThat(session().status().isFailure).isTrue()
        }
    }

    @Test
    fun `uncertain consumption is never retried by this session`() = runTest {
        val subject = session()
        coEvery { http.post(any(), any(), any()) } throws IOException("lost synthetic reply")
        assertThat(subject.consume().isFailure).isTrue()
        assertThat(subject.consume().isFailure).isTrue()
        coVerify(exactly = 1) { http.post(any(), any(), any()) }
    }

    @Test
    fun `a grant for another identity never reaches native login`() = runTest {
        coEvery { http.post(any(), any(), any()) } returns JSONObject()
        .put("user_id", "@other:example")
        .put("login_token", "synthetic-grant")
            .put("expires_in_ms", 60_000)
        assertThat(session().consume().isFailure).isTrue()
    }
}
