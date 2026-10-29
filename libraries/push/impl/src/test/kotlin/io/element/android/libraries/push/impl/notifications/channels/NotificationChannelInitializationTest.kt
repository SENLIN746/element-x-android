/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.push.impl.notifications.channels

import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.NotificationSound
import io.element.android.libraries.preferences.api.store.NotificationSoundChannelConfig
import io.element.android.libraries.preferences.test.InMemoryAppPreferencesStore
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class NotificationChannelInitializationTest : RobolectricTest() {
    @Test
    fun `construction does not read preferences and preparation suspends without blocking the session callback`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val reads = AtomicInteger()
        val stored = InMemoryAppPreferencesStore(messageSound = NotificationSound.ElementDefault)
        val preferences = object : AppPreferencesStore by stored {
            override suspend fun getNotificationSoundChannelConfig(): NotificationSoundChannelConfig {
                reads.incrementAndGet()
                entered.complete(Unit)
                release.await()
                return stored.getNotificationSoundChannelConfig()
            }
        }
        val manager = mockk<NotificationManagerCompat>(relaxed = true) {
            every { notificationChannels } returns emptyList()
        }
        val channels = createNotificationChannels(initialize = false, notificationManager = manager, appPreferencesStore = preferences)
        assertThat(reads.get()).isEqualTo(0)
        verify(exactly = 0) { manager.createNotificationChannel(any<NotificationChannelCompat>()) }
        val first = launch { channels.initialize() }
        val second = launch { channels.initialize() }
        entered.await()
        assertThat(first.isCompleted).isFalse()
        assertThat(second.isCompleted).isFalse()
        release.complete(Unit)
        first.join()
        second.join()
        assertThat(reads.get()).isEqualTo(1)
        verify(exactly = 4) { manager.createNotificationChannel(any<NotificationChannelCompat>()) }
    }

    @Test
    fun `initialization preserves sound selected after an older snapshot was taken`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val stored = InMemoryAppPreferencesStore(messageSound = NotificationSound.ElementDefault, messageSoundChannelVersion = 2)
        val preferences = object : AppPreferencesStore by stored {
            override suspend fun getNotificationSoundChannelConfig(): NotificationSoundChannelConfig {
                val snapshot = stored.getNotificationSoundChannelConfig()
                entered.complete(Unit)
                release.await()
                return snapshot
            }
        }
        val channels = createNotificationChannels(initialize = false, appPreferencesStore = preferences)
        val initialization = launch { channels.initialize() }
        entered.await()
        channels.recreateNoisyChannel(NotificationSound.Silent, version = 7)
        release.complete(Unit)
        initialization.join()
        assertThat(channels.getChannelIdForTest()).isEqualTo(noisyNotificationChannelId(7))
    }
}
