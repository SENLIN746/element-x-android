/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.login

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.agentim.api.onboarding.AgentimOnboardingService
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
    private val onboarding = mockk<AgentimOnboardingService>()
    private val subject = AgentimDeviceLogin(authentication, onboarding)

    @Before
    fun prepare() {
        coEvery { authentication.cancelOAuthLogin() } returns Result.success(Unit)
    }

    @Test
    fun `cross origin delegation is rejected before pairing grant is consumed`() = runTest {
        coEvery { authentication.setHomeserver("https://entry.example") } returns
            Result.success(MatrixHomeServerDetails("https://delegated.example", false, false))
        assertThat(subject.prepare("https://entry.example").exceptionOrNull()).isInstanceOf(AgentimHomeserverOriginChanged::class.java)
        coVerify(exactly = 0) { authentication.loginWithToken(any()) }
    }

    @Test
    fun `creation delegates to recoverable public onboarding without a permit`() = runTest {
        coEvery { onboarding.register("https://hs.example", "agent", "Agent") } returns Result.success(A_USER_ID.value)
        assertThat(subject.register("https://hs.example", "agent", "Agent").getOrThrow()).isEqualTo(A_USER_ID)
        coVerify(exactly = 0) { authentication.setHomeserver(any()) }
    }

    @Test
    fun `credential bearing homeserver URLs are rejected before SDK discovery`() = runTest {
        assertThat(subject.prepare("https://user:secret@hs.example").isFailure).isTrue()
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
}
