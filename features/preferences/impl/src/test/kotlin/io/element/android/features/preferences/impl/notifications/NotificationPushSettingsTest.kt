/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.preferences.impl.notifications

import androidx.compose.runtime.mutableIntStateOf
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.verification.FakeSessionVerificationService
import io.element.android.libraries.push.api.PusherRegistrationFailure
import io.element.android.libraries.push.test.FakePushService
import io.element.android.libraries.pushproviders.api.Distributor
import io.element.android.libraries.pushproviders.test.FakePushProvider
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NotificationPushSettingsTest {
    @Test
    fun `unverified device never registers even after a provider is selected`() = runTest {
        var registrations = 0
        val client = FakeMatrixClient(sessionVerificationService = FakeSessionVerificationService(SessionVerifiedStatus.NotVerified))
        val service = FakePushService(
            availablePushProviders = listOf(FakePushProvider()),
            registerWithLambda = { _, _, _ ->
                registrations++
                Result.success(Unit)
            },
            ensurePusherIsRegisteredResult = {
                registrations++
                Result.success(Unit)
            }
        )
        val presenter = Presenter { rememberNotificationPushSettings(client, service, testCoroutineDispatchers(true), true, 0) }
        presenter.test {
            val state = consumeItemsUntilPredicate { it.registration == PushRegistrationState.NeedsVerification && it.available.isNotEmpty() }.last()
            state.select(0)
            consumeItemsUntilPredicate { !it.showPicker }
            assertThat(registrations).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `installing a distributor and returning retries through the native service`() = runTest {
        var installed = false
        var registrations = 0
        val refresh = mutableIntStateOf(0)
        val distributor = Distributor("synthetic", "Synthetic distributor")
        val provider = object : io.element.android.libraries.pushproviders.api.PushProvider by FakePushProvider() {
            override fun getDistributors() = if (installed) listOf(distributor) else emptyList()
            override suspend fun getCurrentDistributor(sessionId: io.element.android.libraries.matrix.api.core.SessionId) = distributor
        }
        val client = FakeMatrixClient(sessionVerificationService = FakeSessionVerificationService(SessionVerifiedStatus.Verified))
        val service = FakePushService(
            availablePushProviders = listOf(provider),
            ensurePusherIsRegisteredResult = {
                registrations++
                Result.success(Unit)
            }
        )
        val presenter = Presenter { rememberNotificationPushSettings(client, service, testCoroutineDispatchers(true), true, refresh.intValue) }
        presenter.test {
            consumeItemsUntilPredicate { it.registration == PushRegistrationState.NeedsDistributor }
            assertThat(registrations).isEqualTo(0)
            installed = true
            refresh.intValue++
            val registered = consumeItemsUntilPredicate { it.registration == PushRegistrationState.Registered }.last()
            assertThat(registered.available).containsExactly(distributor)
            assertThat(registrations).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `native registration errors are visible and retry can recover`() = runTest {
        var fails = true
        val client = FakeMatrixClient(sessionVerificationService = FakeSessionVerificationService(SessionVerifiedStatus.Verified))
        val service = FakePushService(
            availablePushProviders = listOf(FakePushProvider()),
            ensurePusherIsRegisteredResult = { if (fails) Result.failure(IllegalStateException("Synthetic failure")) else Result.success(Unit) }
        )
        val presenter = Presenter { rememberNotificationPushSettings(client, service, testCoroutineDispatchers(true), true, 0) }
        presenter.test {
            val failed = consumeItemsUntilPredicate { it.registration == PushRegistrationState.Failed }.last()
            fails = false
            failed.retry()
            consumeItemsUntilPredicate { it.registration == PushRegistrationState.Registered }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `verification from another device enables registration and never grants trust locally`() = runTest {
        val verification = FakeSessionVerificationService(SessionVerifiedStatus.NotVerified)
        val client = FakeMatrixClient(sessionVerificationService = verification)
        val service = FakePushService(availablePushProviders = listOf(FakePushProvider()), ensurePusherIsRegisteredResult = { Result.success(Unit) })
        val presenter = Presenter { rememberNotificationPushSettings(client, service, testCoroutineDispatchers(true), true, 0) }
        presenter.test {
            consumeItemsUntilPredicate { it.registration == PushRegistrationState.NeedsVerification }
            verification.emitVerifiedStatus(SessionVerifiedStatus.Verified)
            consumeItemsUntilPredicate { it.registration == PushRegistrationState.Registered }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry rediscovers a provider after a package manager discovery error`() = runTest {
        var fails = true
        val provider = object : io.element.android.libraries.pushproviders.api.PushProvider by FakePushProvider() {
            override fun getDistributors(): List<Distributor> {
                if (fails) error("Synthetic package manager error")
                return listOf(Distributor("synthetic", "Synthetic distributor"))
            }
        }
        val client = FakeMatrixClient(sessionVerificationService = FakeSessionVerificationService(SessionVerifiedStatus.Verified))
        val service = FakePushService(availablePushProviders = listOf(provider), ensurePusherIsRegisteredResult = { Result.success(Unit) })
        val presenter = Presenter { rememberNotificationPushSettings(client, service, testCoroutineDispatchers(true), true, 0) }
        presenter.test {
            val failed = consumeItemsUntilPredicate { it.registration == PushRegistrationState.Failed }.last()
            fails = false
            failed.retry()
            consumeItemsUntilPredicate { it.registration == PushRegistrationState.Registered }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `native failure types keep missing verification distinct from missing distributor`() {
        assertThat(pushRegistrationFailure(PusherRegistrationFailure.AccountNotVerified())).isEqualTo(PushRegistrationState.NeedsVerification)
        assertThat(pushRegistrationFailure(PusherRegistrationFailure.NoDistributorsAvailable())).isEqualTo(PushRegistrationState.NeedsDistributor)
    }
}
