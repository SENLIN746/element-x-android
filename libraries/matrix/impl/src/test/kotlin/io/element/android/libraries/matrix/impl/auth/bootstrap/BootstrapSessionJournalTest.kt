/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.impl.auth.bootstrap

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.androidutils.crypto.BootstrapFiles
import io.element.android.libraries.androidutils.crypto.ClientSecret
import io.element.android.libraries.matrix.api.paths.SessionPaths
import io.element.android.libraries.matrix.impl.auth.MatrixTokenLogin
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.Session
import org.matrix.rustcomponents.sdk.SlidingSyncVersion
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BootstrapSessionJournalTest {
    private val files = mockk<BootstrapFiles>()
    private val sessions = mockk<SessionStore>()
    private val tokens = mockk<MatrixTokenLogin>()
    private val client = mockk<Client>()
    private var durable: ByteArray? = null

    private fun journal(): BootstrapSessionJournal {
        every { files.read(any()) } answers { durable }
        every { files.write(any(), any()) } answers { durable = secondArg<ByteArray>().copyOf() }
        return BootstrapSessionJournal(files)
    }

    @Test
    fun `token response is journaled without initializing SDK and preserves exact stores after restart`() = runTest {
        val session = Session(
            accessToken = "synthetic-access",
            refreshToken = "synthetic-refresh",
            userId = "@agent:example",
            deviceId = "bound-device",
            homeserverUrl = "https://example",
            oauthData = null,
            slidingSyncVersion = SlidingSyncVersion.NATIVE,
        )
        coEvery { tokens.exchange(client, "synthetic-grant", "bound-device") } returns session
        val bootstrap = MatrixBootstrapLogin(journal(), sessions, tokens)
        val paths = SessionPaths(File("/private/synthetic/session"), File("/private/synthetic/cache"))
        val result = bootstrap.create(client, "synthetic-grant", "@agent:example", "bound-device", paths, ClientSecret.Passphrase("synthetic-db-key"))
        assertThat(durable).isNotNull()
        coVerify(exactly = 0) { client.restoreSession(any()) }
        coEvery { sessions.getSession("@agent:example") } returns null
        val restarted = MatrixBootstrapLogin(journal(), sessions, tokens)
        val recovered = requireNotNull(restarted.existing("https://example", "@agent:example", "bound-device"))
        assertThat(recovered.sessionPath).isEqualTo(result.sessionPath)
        assertThat(recovered.cachePath).isEqualTo(result.cachePath)
        assertThat(recovered.passphrase).isEqualTo(result.passphrase)
        assertThat(recovered.accessToken).isEqualTo(result.accessToken)
        assertThat(recovered.refreshToken).isEqualTo(result.refreshToken)
    }

    @Test
    fun `foreign user or device response cannot become recovery material`() = runTest {
        coEvery { tokens.exchange(any(), any(), any()) } returns
            Session(
                accessToken = "synthetic-access",
                refreshToken = null,
                userId = "@other:example",
                deviceId = "bound-device",
                homeserverUrl = "https://example",
                oauthData = null,
                slidingSyncVersion = SlidingSyncVersion.NATIVE,
            )
        val bootstrap = MatrixBootstrapLogin(journal(), sessions, tokens)
        val paths = SessionPaths(File("/synthetic/session"), File("/synthetic/cache"))
        val failed = runCatching { bootstrap.create(client, "synthetic", "@agent:example", "bound-device", paths, ClientSecret.Passphrase("key")) }
        assertThat(failed.isFailure).isTrue()
        assertThat(durable).isNull()
    }
}
