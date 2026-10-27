/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.pairing

import android.util.Base64
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PairingServiceTest {
    private val http = mockk<PairingHttp>()
    private val service = DefaultAgentimPairingService(http)

    private fun response(body: JSONObject) = JSONObject(body.toString())
        .put("pairing_id", "p".repeat(43))
        .put("user_code", "ABCD-EFGH")
        .put("state", "pending")
        .put("created_at_ms", 1_000)
        .put("expires_at_ms", 301_000)
        .put("server_time_ms", 1_000)
        .put("poll_interval_ms", 2_000)

    @Test
    fun `new sessions create distinct proofs and expose only the S256 challenge`() = runTest {
        val created = mutableListOf<JSONObject>()
        var proof = ""
        coEvery { http.post(any(), any(), any()) } coAnswers {
            val body = arg<JSONObject>(2)
            if (arg<List<String>>(1).last() == "status") {
                proof = body.getString("verifier")
                response(created.last())
            } else {
                created += JSONObject(body.toString())
                response(body)
            }
        }
        val first = service.start("https://agents.example", "@agent:example", "Android").getOrThrow()
        first.status().getOrThrow()
        val hash = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(proof.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        assertThat(proof).hasLength(43)
        assertThat(first.request.codeChallenge).isEqualTo(hash)
        assertThat(created.first().has("verifier")).isFalse()
        val second = service.start("https://agents.example", "@agent:example", "Android").getOrThrow()
        assertThat(second.request.codeChallenge).isNotEqualTo(first.request.codeChallenge)
    }

    @Test
    fun `credential bearing URLs malformed identities and device names do not reach network`() = runTest {
        assertThat(service.start("https://u:secret@agents.example", "@agent:example", "Android").isFailure).isTrue()
        assertThat(service.start("https://agents.example", "invalid-agent", "Android").isFailure).isTrue()
        assertThat(service.start("https://agents.example", "@agent:example", "bad\nname").isFailure).isTrue()
        coVerify(exactly = 0) { http.post(any(), any(), any()) }
    }

    @Test
    fun `a substituted create response is not accepted as the original request`() = runTest {
        coEvery { http.post(any(), any(), any()) } coAnswers {
            response(arg(2))
        .put("code_challenge", "x".repeat(43))
        }
        assertThat(service.start("https://agents.example", "@agent:example", "Android").isFailure).isTrue()
    }
}
