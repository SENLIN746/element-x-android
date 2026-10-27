/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.login

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.agentim.api.AgentimEnrollmentService
import io.element.android.libraries.agentim.api.pairing.AgentimPairingGrant
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.auth.MatrixHomeServerDetails
import io.element.android.libraries.matrix.test.A_USER_ID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AgentimDeviceLoginTest {
    private val authentication = mockk<MatrixAuthenticationService>()
    private val enrollment = mockk<AgentimEnrollmentService>()
    private val subject = AgentimDeviceLogin(authentication, enrollment)

    @Before
    fun prepare() {
        coEvery { authentication.cancelOAuthLogin() } returns Result.success(Unit)
    }

    @Test
    fun `cross origin delegation is rejected before admission or login token is sent`() = runTest {
        coEvery { authentication.setHomeserver("https://entry.example") } returns
            Result.success(MatrixHomeServerDetails("https://delegated.example", false, false))
        val result = subject.login(request("https://entry.example"))
        assertThat(result.exceptionOrNull()).isInstanceOf(AgentimHomeserverOriginChanged::class.java)
        coVerify(exactly = 0) { enrollment.register(any(), any(), any(), any()) }
        coVerify(exactly = 0) { authentication.loginWithToken(any()) }
    }

    @Test
    fun `same origin enrollment uses the resolved base used by Matrix device login`() = runTest {
        coEvery { authentication.setHomeserver("https://hs.example:443") } returns
            Result.success(MatrixHomeServerDetails("https://hs.example/matrix/", false, false))
        coEvery { enrollment.register("https://hs.example/matrix/", "synthetic-admission", "agent", "Agent") } returns Result.success("short-token")
        coEvery { authentication.loginWithToken("short-token") } returns Result.success(A_USER_ID)
        assertThat(subject.login(request("https://hs.example:443")).getOrNull()).isEqualTo(A_USER_ID)
        coVerify(exactly = 1) { enrollment.register("https://hs.example/matrix/", "synthetic-admission", "agent", "Agent") }
        coVerify(exactly = 1) { authentication.loginWithToken("short-token") }
    }

    @Test
    fun `credential-bearing homeserver URLs are rejected before SDK discovery`() = runTest {
        assertThat(subject.login(request("https://user:secret@hs.example")).isFailure).isTrue()
        coVerify(exactly = 0) { authentication.setHomeserver(any()) }
    }

    @Test
    fun `native credential handoff remains single use after uncertain login`() = runTest {
        val grant = AgentimPairingGrant(A_USER_ID.value, "synthetic-pairing-grant")
        coEvery { authentication.loginWithToken(any()) } returns Result.failure(IllegalStateException("synthetic lost reply"))
        assertThat(subject.connect(grant).isFailure).isTrue()
        assertThat(subject.connect(grant).isFailure).isTrue()
        coVerify(exactly = 1) { authentication.loginWithToken("synthetic-pairing-grant") }
    }

    private fun request(homeserver: String) = AgentimLoginRequest(homeserver, "", "synthetic-admission", "agent", "Agent", true)
}
