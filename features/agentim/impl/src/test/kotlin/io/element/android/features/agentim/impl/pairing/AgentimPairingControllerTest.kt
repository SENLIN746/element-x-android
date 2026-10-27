/*
 * Copyright (c) 2_026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.pairing

import com.google.common.truth.Truth.assertThat
import io.element.android.features.agentim.impl.login.AgentimDeviceLogin
import io.element.android.libraries.agentim.api.pairing.AgentimPairingGrant
import io.element.android.libraries.agentim.api.pairing.AgentimPairingPhase
import io.element.android.libraries.agentim.api.pairing.AgentimPairingRequest
import io.element.android.libraries.agentim.api.pairing.AgentimPairingService
import io.element.android.libraries.agentim.api.pairing.AgentimPairingSession
import io.element.android.libraries.matrix.test.A_USER_ID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

// Virtual time covers the five-minute deadline without a wall-clock wait.
@OptIn(ExperimentalCoroutinesApi::class)
class AgentimPairingControllerTest {
    private val service = mockk<AgentimPairingService>()
    private val login = mockk<AgentimDeviceLogin>()
    private val session = mockk<AgentimPairingSession>()
    private val request = AgentimPairingRequest(
        "p".repeat(43),
        A_USER_ID.value,
        "ABCD-EFGH",
        "c".repeat(43),
        "AgentIM Android",
        "android-app://im.agentim.android",
        AgentimPairingPhase.Pending,
        1_000,
        301_000,
        1_000,
        2_000
    )
    private val grant = AgentimPairingGrant(A_USER_ID.value, "synthetic-short-grant")

    private fun TestScope.subject(): AgentimPairingController {
        coEvery { login.prepare(any()) } returns Result.success("https://agents.example")
        coEvery { service.start(any(), any(), any()) } returns Result.success(session)
        every { session.request } returns request
        coEvery { session.status() } returns Result.success(request.copy(state = AgentimPairingPhase.Approved))
        coEvery { session.consume() } returns Result.success(grant)
        coEvery { session.cancel() } returns Result.success(Unit)
        coEvery { login.connect(grant) } returns Result.success(A_USER_ID)
        return AgentimPairingController(service, login, backgroundScope, { testScheduler.currentTime }).apply {
            active(true)
            edit(AgentimPairingEvent.Homeserver("https://agents.example"))
            edit(AgentimPairingEvent.Target(A_USER_ID.value))
        }
    }

    @Test
    fun `approved request consumes once then connects the independent native device`() = runTest {
        val subject = subject()
        subject.submit()
        runCurrent()
        assertThat(subject.state.value.phase).isEqualTo(PairingScreenPhase.Waiting)
        assertThat(subject.state.value.toString()).doesNotContain("synthetic-short-grant")
        subject.submit()
        advanceTimeBy(2_000)
        runCurrent()
        assertThat(subject.state.value.phase).isEqualTo(PairingScreenPhase.Connected)
        coVerify(exactly = 1) { session.consume() }
        coVerify(exactly = 1) { service.start(any(), any(), any()) }
        coVerify(exactly = 0) { session.cancel() }
    }

    @Test
    fun `cancel stops polling and cleans the exact request without creating a device`() = runTest {
        val subject = subject()
        subject.submit()
        runCurrent()
        subject.cancel()
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertThat(subject.state.value.request).isNull()
        coVerify(exactly = 1) { session.cancel() }
        coVerify(exactly = 0) { session.consume() }
        coVerify(exactly = 0) { login.connect(any()) }
    }

    @Test
    fun `background pauses new polling and foreground rechecks an approved request`() = runTest {
        val subject = subject()
        subject.submit()
        runCurrent()
        subject.active(false)
        advanceTimeBy(10_000)
        runCurrent()
        coVerify(exactly = 0) { session.status() }
        coVerify(exactly = 0) { session.consume() }
        subject.active(true)
        runCurrent()
        assertThat(subject.state.value.phase).isEqualTo(PairingScreenPhase.Connected)
        coVerify(exactly = 1) { session.consume() }
    }

    @Test
    fun `expiry after background sleep never consumes an earlier approval`() = runTest {
        val subject = subject()
        subject.submit()
        runCurrent()
        subject.active(false)
        advanceTimeBy(300_000)
        runCurrent()
        subject.active(true)
        runCurrent()
        assertThat(subject.state.value.phase).isEqualTo(PairingScreenPhase.Expired)
        coVerify(exactly = 0) { session.consume() }
    }

    @Test
    fun `Agent rejection is terminal and grants no device`() = runTest {
        val subject = subject()
        coEvery { session.status() } returns Result.success(request.copy(state = AgentimPairingPhase.Rejected))
        subject.submit()
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        assertThat(subject.state.value.phase).isEqualTo(PairingScreenPhase.Rejected)
        coVerify(exactly = 0) { session.consume() }
    }

    @Test
    fun `lost consume response is never retried and a new request must be explicit`() = runTest {
        val subject = subject()
        coEvery { session.consume() } returns Result.failure(IOException("synthetic uncertain result"))
        subject.submit()
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        advanceTimeBy(20_000)
        runCurrent()
        assertThat(subject.state.value.phase).isEqualTo(PairingScreenPhase.Failed)
        coVerify(exactly = 1) { session.consume() }
        coVerify(exactly = 0) { login.connect(any()) }
    }
}
