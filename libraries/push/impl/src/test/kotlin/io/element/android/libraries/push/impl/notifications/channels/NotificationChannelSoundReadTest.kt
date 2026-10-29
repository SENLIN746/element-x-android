/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.notifications.channels

import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.preferences.api.store.NotificationSound
import io.element.android.libraries.preferences.test.InMemoryAppPreferencesStore
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class NotificationChannelSoundReadTest : RobolectricTest() {
    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `readNoisyChannelSound - returns Custom for non-default URI`() = runTest {
        val customUri = "content://media/internal/audio/media/42".toUri()
        val channel = mockk<android.app.NotificationChannel>(relaxed = true) {
            every { sound } returns customUri
        }
        val notificationManager = mockk<NotificationManagerCompat>(relaxed = true) {
            every { notificationChannels } returns emptyList()
            every { getNotificationChannel(NOISY_NOTIFICATION_CHANNEL_ID_BASE) } returns channel
        }
        val channels = createNotificationChannels(notificationManager = notificationManager)

        val result = channels.readNoisyChannelSound()

        assertThat(result).isEqualTo(NotificationSound.Custom(customUri.toString()))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `readNoisyChannelSound - classifies the bundled message URI as ElementDefault`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val ourBundled = "android.resource://${context.packageName}/${io.element.android.libraries.push.impl.R.raw.message}".toUri()
        val channel = mockk<android.app.NotificationChannel>(relaxed = true) {
            every { sound } returns ourBundled
        }
        val notificationManager = mockk<NotificationManagerCompat>(relaxed = true) {
            every { notificationChannels } returns emptyList()
            every { getNotificationChannel(NOISY_NOTIFICATION_CHANNEL_ID_BASE) } returns channel
        }
        val channels = createNotificationChannels(notificationManager = notificationManager)

        val result = channels.readNoisyChannelSound()

        assertThat(result).isEqualTo(NotificationSound.ElementDefault)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `readNoisyChannelSound - classifies the bundled fade URI as ElementFade`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val ourFade = "android.resource://${context.packageName}/${io.element.android.libraries.push.impl.R.raw.element_fade}".toUri()
        val channel = mockk<android.app.NotificationChannel>(relaxed = true) {
            every { sound } returns ourFade
        }
        val notificationManager = mockk<NotificationManagerCompat>(relaxed = true) {
            every { notificationChannels } returns emptyList()
            every { getNotificationChannel(NOISY_NOTIFICATION_CHANNEL_ID_BASE) } returns channel
        }
        val channels = createNotificationChannels(notificationManager = notificationManager)

        val result = channels.readNoisyChannelSound()

        assertThat(result).isEqualTo(NotificationSound.ElementFade)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `readNoisyChannelSound - classifies DEFAULT_NOTIFICATION_URI as SystemDefault`() = runTest {
        val channel = mockk<android.app.NotificationChannel>(relaxed = true) {
            every { sound } returns Settings.System.DEFAULT_NOTIFICATION_URI
        }
        val notificationManager = mockk<NotificationManagerCompat>(relaxed = true) {
            every { notificationChannels } returns emptyList()
            every { getNotificationChannel(NOISY_NOTIFICATION_CHANNEL_ID_BASE) } returns channel
        }
        val channels = createNotificationChannels(notificationManager = notificationManager)

        val result = channels.readNoisyChannelSound()

        assertThat(result).isEqualTo(NotificationSound.SystemDefault)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `init - migrates legacy SystemDefault message sound to ElementDefault when version is 0`() = runTest {
        val notificationManager = mockk<NotificationManagerCompat>(relaxed = true) {
            every { notificationChannels } returns emptyList()
        }
        val appPreferencesStore = InMemoryAppPreferencesStore(
            messageSound = NotificationSound.SystemDefault,
            messageSoundChannelVersion = 0,
        )

        createNotificationChannels(
            notificationManager = notificationManager,
            appPreferencesStore = appPreferencesStore,
        )

        val config = appPreferencesStore.getNotificationSoundChannelConfig()
        assertThat(config.messageSound).isEqualTo(NotificationSound.ElementDefault)
        assertThat(config.messageSoundVersion).isEqualTo(1)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `init - migration is idempotent across multiple instantiations`() = runTest {
        val notificationManager = mockk<NotificationManagerCompat>(relaxed = true) {
            every { notificationChannels } returns emptyList()
        }
        val appPreferencesStore = InMemoryAppPreferencesStore(
            messageSound = NotificationSound.SystemDefault,
            messageSoundChannelVersion = 0,
        )

        // First boot: migrate.
        createNotificationChannels(notificationManager = notificationManager, appPreferencesStore = appPreferencesStore)
        val afterFirst = appPreferencesStore.getNotificationSoundChannelConfig()
        assertThat(afterFirst.messageSound).isEqualTo(NotificationSound.ElementDefault)
        assertThat(afterFirst.messageSoundVersion).isEqualTo(1)

        // Second boot: gate (version == 0) no longer matches, so the version must not bump again.
        createNotificationChannels(notificationManager = notificationManager, appPreferencesStore = appPreferencesStore)
        val afterSecond = appPreferencesStore.getNotificationSoundChannelConfig()
        assertThat(afterSecond.messageSound).isEqualTo(NotificationSound.ElementDefault)
        assertThat(afterSecond.messageSoundVersion).isEqualTo(1)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `init - does not re-migrate when message version is non-zero`() = runTest {
        val notificationManager = mockk<NotificationManagerCompat>(relaxed = true) {
            every { notificationChannels } returns emptyList()
        }
        val appPreferencesStore = InMemoryAppPreferencesStore(
            messageSound = NotificationSound.SystemDefault,
            messageSoundChannelVersion = 5,
        )

        createNotificationChannels(
            notificationManager = notificationManager,
            appPreferencesStore = appPreferencesStore,
        )

        val config = appPreferencesStore.getNotificationSoundChannelConfig()
        assertThat(config.messageSound).isEqualTo(NotificationSound.SystemDefault)
        assertThat(config.messageSoundVersion).isEqualTo(5)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `init - does not migrate when persisted message sound is not SystemDefault`() = runTest {
        val notificationManager = mockk<NotificationManagerCompat>(relaxed = true) {
            every { notificationChannels } returns emptyList()
        }
        val appPreferencesStore = InMemoryAppPreferencesStore(
            messageSound = NotificationSound.Silent,
            messageSoundChannelVersion = 0,
        )

        createNotificationChannels(
            notificationManager = notificationManager,
            appPreferencesStore = appPreferencesStore,
        )

        val config = appPreferencesStore.getNotificationSoundChannelConfig()
        assertThat(config.messageSound).isEqualTo(NotificationSound.Silent)
        assertThat(config.messageSoundVersion).isEqualTo(0)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `readNoisyChannelSound - returns Silent when channel sound is null`() = runTest {
        val channel = mockk<android.app.NotificationChannel>(relaxed = true) {
            every { sound } returns null
        }
        val notificationManager = mockk<NotificationManagerCompat>(relaxed = true) {
            every { notificationChannels } returns emptyList()
            every { getNotificationChannel(NOISY_NOTIFICATION_CHANNEL_ID_BASE) } returns channel
        }
        val channels = createNotificationChannels(notificationManager = notificationManager)

        val result = channels.readNoisyChannelSound()

        assertThat(result).isEqualTo(NotificationSound.Silent)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `readNoisyChannelSound - returns null when channel does not exist`() = runTest {
        val notificationManager = mockk<NotificationManagerCompat>(relaxed = true) {
            every { notificationChannels } returns emptyList()
            every { getNotificationChannel(NOISY_NOTIFICATION_CHANNEL_ID_BASE) } returns null
        }
        val channels = createNotificationChannels(notificationManager = notificationManager)

        val result = channels.readNoisyChannelSound()

        assertThat(result).isNull()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `readRingingCallChannelSound - returns SystemDefault when channel sound matches DEFAULT_RINGTONE_URI`() = runTest {
        val channel = mockk<android.app.NotificationChannel>(relaxed = true) {
            every { sound } returns Settings.System.DEFAULT_RINGTONE_URI
        }
        val notificationManager = mockk<NotificationManagerCompat>(relaxed = true) {
            every { notificationChannels } returns emptyList()
            every { getNotificationChannel(RINGING_CALL_NOTIFICATION_CHANNEL_ID_BASE) } returns channel
        }
        val channels = createNotificationChannels(notificationManager = notificationManager)

        val result = channels.readRingingCallChannelSound()

        assertThat(result).isEqualTo(NotificationSound.SystemDefault)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `readRingingCallChannelSound - returns Custom for non-default URI`() = runTest {
        val customUri = "content://media/external/audio/media/9001".toUri()
        val channel = mockk<android.app.NotificationChannel>(relaxed = true) {
            every { sound } returns customUri
        }
        val notificationManager = mockk<NotificationManagerCompat>(relaxed = true) {
            every { notificationChannels } returns emptyList()
            every { getNotificationChannel(RINGING_CALL_NOTIFICATION_CHANNEL_ID_BASE) } returns channel
        }
        val channels = createNotificationChannels(notificationManager = notificationManager)

        val result = channels.readRingingCallChannelSound()

        assertThat(result).isEqualTo(NotificationSound.Custom(customUri.toString()))
    }
}
