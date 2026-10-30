/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.account

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.androidutils.crypto.BootstrapFiles
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.auth.MatrixHomeServerDetails
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AccountAccessTest {
    private val files = mockk<BootstrapFiles>()
    private val records = mutableMapOf<String, ByteArray>()
    private val http = mockk<AccountHttp>()
    private val sessions = mockk<SessionStore>()
    private val home = "https://example.org"
    private val user = "@agent:example.org"

    private fun setup(): AccountVault {
        every { files.read(any()) } answers { records[firstArg()] }
        every { files.write(any(), any()) } answers { records[firstArg()] = secondArg<ByteArray>().copyOf() }
        every { http.base(home) } returns home
        coEvery { sessions.getSession(user) } returns aSessionData(sessionId = user).copy(homeserverUrl = home, isTokenValid = true)
        return AccountVault(files)
    }
    @Test
    fun `portable proof is private and rejects extra recovery secrets`() {
        val key = AccountCredential(home, user, "a".repeat(43), "b".repeat(43))
        assertThat(key.toString()).doesNotContain(key.secret)
        assertThat(AccountCredential.parse(key.json()).secret).isEqualTo(key.secret)
        assertThat(runCatching { AccountCredential.parse(key.json().put("recovery_secret", "chat-key")) }.isFailure).isTrue()
    }
    @Test
    fun `bind failure keeps the same private key for a retry and never reports ready`() = runTest {
        val vault = setup()
        coEvery { http.request(home, match { it.endsWith("/credentials") }, any(), any()) } throws java.io.IOException("synthetic lost response")
        val keys = AccountKeyManager(vault, http, sessions)
        assertThat(runCatching { keys.ensure(user) }.isFailure).isTrue()
        val before = requireNotNull(vault.read(home, user))
        assertThat(before.getBoolean("ready")).isFalse()
        coEvery { http.request(home, any(), any(), any()) } answers {
            val body = thirdArg<JSONObject>()
            JSONObject().put("credential_id", body.getString("credential_id")).put("user_id", user).put("revoked", false)
        }
        val key = keys.ensure(user)
        assertThat(key.id).isEqualTo(before.getJSONObject("credential").getString("credential_id"))
        assertThat(vault.read(home, user)?.getBoolean("ready")).isTrue()
    }
    @Test
    fun `lost account login response reuses attempt and device and does not expose grants to UI`() = runTest {
        val vault = setup()
        val keys = mockk<AccountKeyManager>()
        val authentication = mockk<MatrixAuthenticationService>()
        val key = AccountCredential(home, user, "a".repeat(43), "b".repeat(43))
        coEvery { keys.prove(any()) } returns Unit
        coEvery { authentication.resumeAgentBootstrap(any(), any(), any()) } returns Result.success(null)
        coEvery { authentication.cancelOAuthLogin() } returns Result.success(Unit)
        val details = mockk<MatrixHomeServerDetails>()
        every { details.url } returns home
        coEvery { authentication.setHomeserver(home) } returns Result.success(details)
        coEvery { http.request(home, any(), any(), any()) } throws java.io.IOException("synthetic lost response")
        val login = AccountLogin(vault, keys, http, authentication)
        assertThat(runCatching { login.restore(key) }.isFailure).isTrue()
        val pending = requireNotNull(vault.pending())
        coEvery { http.request(home, any(), any(), any()) } answers {
            val body = thirdArg<JSONObject>()
            assertThat(body.getString("attempt_id")).isEqualTo(pending.getString("attempt"))
            assertThat(body.getString("device_id")).isEqualTo(pending.getString("device"))
            JSONObject().put("user_id", user).put("attempt_id", body.getString("attempt_id"))
                .put("device_id", body.getString("device_id")).put("access_token", "synthetic-native-token")
        }
        coEvery { authentication.loginWithAccountSession(any(), any(), any(), any()) } returns Result.success(SessionId(user))
        assertThat(login.resume()).isEqualTo(user)
        coVerify(exactly = 1) { authentication.loginWithAccountSession("synthetic-native-token", null, user, pending.getString("device")) }
        assertThat(runCatching { vault.begin(AccountCredential(home, "@other:example.org", key.id, key.secret)) }.isFailure).isTrue()
    }
}
