/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.onboarding

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.agentim.api.onboarding.AgentimRegistrationRejected
import io.element.android.libraries.androidutils.crypto.BootstrapFiles
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.auth.MatrixHomeServerDetails
import io.element.android.libraries.matrix.api.core.SessionId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OnboardingServiceTest {
    private val base = "https://agents.example"
    private val path = "/_synapse/client/agentim/v2/registration"
    private val http = mockk<OnboardingHttp>()
    private val files = mockk<BootstrapFiles>()
    private val auth = mockk<MatrixAuthenticationService>()
    private var durable: ByteArray? = null
    private val attempts = OnboardingAttempts(files)

    @Before
    fun setup() {
        every { files.read(any()) } answers { durable }
        every { files.write(any(), any()) } answers { durable = secondArg<ByteArray>().copyOf() }
        every { files.delete(any()) } answers { durable = null }
        coEvery { http.base(any()) } answers { firstArg() }
        coEvery { auth.cancelOAuthLogin() } returns Result.success(Unit)
        coEvery { auth.setHomeserver(base) } returns Result.success(MatrixHomeServerDetails(base, false, false))
        coEvery { auth.resumeAgentBootstrap(any(), any(), any()) } returns Result.success(null)
        coEvery { http.request(base, path, null, null) } returns policy("open")
    }

    @Test
    fun `proof is durable before allocation and reused after lost create reply`() = runTest {
        val service = DefaultAgentimOnboardingService(http, attempts, auth, mockk(), backgroundScope)
        var first: String? = null
        coEvery { http.request(base, path, ofType(JSONObject::class), null) } coAnswers {
            val body = arg<JSONObject>(2)
            val pending = requireNotNull(OnboardingAttempts(files).pending())
            assertThat(body.getString("attempt_id")).isEqualTo(pending.attemptId)
            assertThat(body.getString("device_id")).isEqualTo(pending.deviceId)
            assertThat(body.getString("code_challenge")).isEqualTo(pending.challenge())
            assertThat(body.has("verifier")).isFalse()
            first?.let { assertThat(body.toString()).isEqualTo(it) }
            first = body.toString()
            error("synthetic lost reply")
        }
        assertThat(service.register(base, "agent", "Agent").isFailure).isTrue()
        val restarted = DefaultAgentimOnboardingService(http, OnboardingAttempts(files), auth, mockk(), backgroundScope)
        assertThat(restarted.register(base, "agent", "Agent").isFailure).isTrue()
        coVerify(exactly = 2) { http.request(base, path, ofType(JSONObject::class), null) }
    }

    @Test
    fun `closed or unknown capability cannot allocate an identity or proof`() = runTest {
        val service = DefaultAgentimOnboardingService(http, attempts, auth, mockk(), backgroundScope)
        coEvery { http.request(base, path, null, null) } returns policy("closed")
        assertThat(service.register(base, "agent", "").isFailure).isTrue()
        coEvery { http.request(base, path, null, null) } throws IllegalStateException("offline")
        assertThat(service.register(base, "agent", "").isFailure).isTrue()
        assertThat(durable).isNull()
        coVerify(exactly = 0) { auth.setHomeserver(any()) }
    }

    @Test
    fun `original native session resumes before discovery can rotate its store`() = runTest {
        val attempt = attempts.begin(base, "agent", "")
        attempts.bind(attempt, "@agent:example")
        coEvery { auth.resumeAgentBootstrap(base, "@agent:example", attempt.deviceId) } returns Result.success(SessionId("@agent:example"))
        val service = DefaultAgentimOnboardingService(http, attempts, auth, mockk(), backgroundScope)
        assertThat(service.register(base, "agent", "").getOrThrow()).isEqualTo("@agent:example")
        coVerify(exactly = 0) { auth.setHomeserver(any()) }
        coVerify(exactly = 0) { http.request(any(), any(), any(), any()) }
    }

    @Test
    fun `consume binds native first device and rejects grant substitution`() = runTest {
        val attempt = attempts.begin(base, "agent", "")
        val status = JSONObject().put("attempt_id", attempt.attemptId).put("device_id", attempt.deviceId)
            .put("expires_at_ms", 2_000_000_000_000L).put("state", "reserved")
        coEvery { http.request(base, path, ofType(JSONObject::class), null) } returns status
        val grant = JSONObject(status.toString()).put("state", "provisioned").put("user_id", "@agent:example")
            .put("login_token", "synthetic-token").put("expires_in_ms", 60_000)
        coEvery { http.request(base, "$path/${attempt.attemptId}/consume", any(), null) } returns grant
        coEvery { auth.loginWithAgentBootstrap("synthetic-token", "@agent:example", attempt.deviceId) } coAnswers {
            assertThat(OnboardingAttempts(files).pending()?.userId).isEqualTo("@agent:example")
            Result.success(SessionId("@agent:example"))
        }
        val service = DefaultAgentimOnboardingService(http, attempts, auth, mockk(), backgroundScope)
        assertThat(service.register(base, "agent", "").getOrThrow()).isEqualTo("@agent:example")
        grant.put("device_id", "foreign-device")
        assertThat(service.register(base, "agent", "").isFailure).isTrue()
        coVerify(exactly = 1) { auth.loginWithAgentBootstrap(any(), any(), any()) }
    }

    @Test
    fun `discovered foreign origin receives neither proof nor registration`() = runTest {
        coEvery { auth.setHomeserver(base) } returns Result.success(MatrixHomeServerDetails("https://foreign.example", false, false))
        val service = DefaultAgentimOnboardingService(http, attempts, auth, mockk(), backgroundScope)
        assertThat(service.register(base, "agent", "").isFailure).isTrue()
        coVerify(exactly = 0) { http.request(base, path, ofType(JSONObject::class), null) }
    }

    @Test
    fun `definitive name rejection allows another name but unknown failure preserves proof`() = runTest {
        val service = DefaultAgentimOnboardingService(http, attempts, auth, mockk(), backgroundScope)
        coEvery { http.request(base, path, ofType(JSONObject::class), null) } throws AgentimRegistrationRejected("M_USER_IN_USE")
        assertThat(service.register(base, "occupied", "").isFailure).isTrue()
        assertThat(durable).isNull()
        coEvery { http.request(base, path, ofType(JSONObject::class), null) } throws IllegalStateException("lost reply")
        assertThat(service.register(base, "available", "").isFailure).isTrue()
        assertThat(attempts.pending()?.localpart).isEqualTo("available")
    }

    private fun policy(mode: String) = JSONObject().put("mode", mode).put("protocol", 2).put("attempt_lifetime_ms", 86_400_000L)
}
