/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.impl.auth

import com.google.common.truth.Truth.assertThat
import io.element.android.features.enterprise.test.FakeEnterpriseService
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.api.paths.SessionPaths
import io.element.android.libraries.matrix.impl.ClientBuilderProvider
import io.element.android.libraries.matrix.impl.FakeClientBuilderProvider
import io.element.android.libraries.matrix.impl.createRustMatrixClientFactory
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiClient
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiClientBuilder
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiHomeserverLoginDetails
import io.element.android.libraries.matrix.impl.paths.SessionPathsFactory
import io.element.android.libraries.matrix.impl.storage.FakeSqliteStoreBuilder
import io.element.android.libraries.matrix.impl.storage.FakeSqliteStoreBuilderProvider
import io.element.android.libraries.matrix.impl.storage.SqliteStoreBuilder
import io.element.android.libraries.matrix.impl.storage.SqliteStoreBuilderProvider
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.auth.FakeOAuthRedirectUrlProvider
import io.element.android.libraries.matrix.test.core.aBuildMeta
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.workmanager.test.FakeWorkManagerScheduler
import io.element.android.tests.testutils.testCoroutineDispatchers
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientBuilder
import java.io.File
import java.io.IOException

class AgentimTokenAuthenticationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `token login requires selected homeserver`() = runTest {
        val service = createRustMatrixAuthenticationService()
        assertThat(service.loginWithToken("single-use-test-token").isFailure).isTrue()
    }

    @Test
    fun `blank token is rejected without accessing the network`() = runTest {
        val tokenLogin = mockk<MatrixTokenLogin>()
        val builder = FakeClientBuilderProvider(provideResult = {
            FakeFfiClientBuilder(buildResult = { FakeFfiClient(homeserverLoginDetailsResult = { FakeFfiHomeserverLoginDetails() }) })
        })
        val service = createRustMatrixAuthenticationService(builder, tokenLogin)
        assertThat(service.setHomeserver("https://example.org").isSuccess).isTrue()
        assertThat(service.loginWithToken(" ").isFailure).isTrue()
        coVerify(exactly = 0) { tokenLogin.login(any(), any()) }
    }

    @Test
    fun `token login closes temporary store before creating the native session client`() = runTest {
        val events = mutableListOf<String>()
        val clients = ArrayDeque<() -> Client>().apply {
            add {
                events.add("build login")
                FakeFfiClient(homeserverLoginDetailsResult = { FakeFfiHomeserverLoginDetails() }, closeResult = { events.add("close login") })
            }
            add {
                events.add("build session")
                FakeFfiClient(withUtdHook = {})
            }
        }
        val builder = object : ClientBuilderProvider {
            override fun provide(): ClientBuilder = FakeFfiClientBuilder(buildResult = clients.removeFirst())
        }
        val tokenLogin = mockk<MatrixTokenLogin>()
        coEvery { tokenLogin.login(any(), "short-token") } coAnswers {
            events.add("consume token")
        }
        val service = createRustMatrixAuthenticationService(builder, tokenLogin)
        assertThat(service.setHomeserver("https://example.org").isSuccess).isTrue()
        assertThat(service.loginWithToken("short-token").getOrNull()).isEqualTo(A_USER_ID)
        assertThat(events).containsExactly("build login", "consume token", "close login", "build session").inOrder()
    }

    @Test
    fun `repeated failed token retries close temporary stores before deleting their paths`() = runTest {
        val events = mutableListOf<String>()
        val paths = mutableListOf<SessionPaths>()
        val storeProvider = object : SqliteStoreBuilderProvider {
            override fun provide(sessionPaths: SessionPaths): SqliteStoreBuilder {
                sessionPaths.fileDirectory.mkdirs()
                sessionPaths.cacheDirectory.mkdirs()
                paths.add(sessionPaths)
                return FakeSqliteStoreBuilder()
            }
        }
        var index = 0
        val builder = FakeClientBuilderProvider(provideResult = {
            FakeFfiClientBuilder(buildResult = {
                val current = index++
                events.add("build $current")
                FakeFfiClient(homeserverLoginDetailsResult = { FakeFfiHomeserverLoginDetails() }, closeResult = {
                    assertThat(paths[current].fileDirectory.exists()).isTrue()
                    events.add("close $current")
                })
            })
        })
        val tokenLogin = mockk<MatrixTokenLogin>()
        coEvery { tokenLogin.login(any(), any()) } throws IOException("expired synthetic token")
        val service = createRustMatrixAuthenticationService(
            builder,
            tokenLogin,
            SessionPathsFactory(temporaryFolder.newFolder("sessions"), temporaryFolder.newFolder("cache")),
            storeProvider,
        )
        repeat(3) {
            assertThat(service.setHomeserver("https://example.org").isSuccess).isTrue()
            assertThat(service.loginWithToken("expired-short-token").isFailure).isTrue()
        }
        assertThat(events).containsExactly("build 0", "close 0", "build 1", "close 1", "build 2").inOrder()
        assertThat(paths[0].fileDirectory.exists()).isFalse()
        assertThat(paths[1].fileDirectory.exists()).isFalse()
        assertThat(paths[2].fileDirectory.exists()).isTrue()
    }

    private fun TestScope.createRustMatrixAuthenticationService(
        clientBuilderProvider: ClientBuilderProvider = FakeClientBuilderProvider(),
        tokenLogin: MatrixTokenLogin = MatrixTokenLogin(),
        sessionPathsFactory: SessionPathsFactory = SessionPathsFactory(File("/base"), File("/cache")),
        sqliteStoreBuilderProvider: SqliteStoreBuilderProvider = FakeSqliteStoreBuilderProvider(),
    ): RustMatrixAuthenticationService {
        val sessionStore = InMemorySessionStore(updateUserProfileResult = { _, _, _, _ -> })
        return RustMatrixAuthenticationService(
            sessionPathsFactory = sessionPathsFactory,
            coroutineDispatchers = testCoroutineDispatchers(),
            sessionStore = sessionStore,
            rustMatrixClientFactory = createRustMatrixClientFactory(
                sessionStore = sessionStore,
                clientBuilderProvider = clientBuilderProvider,
                sqliteStoreBuilderProvider = sqliteStoreBuilderProvider,
                workManagerScheduler = FakeWorkManagerScheduler(submitLambda = {}, cancelLambda = { _, _ -> }),
            ),
            secretGenerator = FakeSecretGenerator(),
            oAuthConfigurationProvider = OAuthConfigurationProvider(aBuildMeta(), FakeOAuthRedirectUrlProvider()),
            enterpriseService = FakeEnterpriseService(),
            featureFlagService = FakeFeatureFlagService(),
            clientEnterpriseHook = {},
            tokenLogin = tokenLogin,
        )
    }
}
