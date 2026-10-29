/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.push.impl.notifications.channels

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.NotificationSound
import io.element.android.libraries.preferences.api.store.NotificationSoundChannelConfig
import io.element.android.libraries.preferences.test.InMemoryAppPreferencesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NotificationSoundConfigurationTest {
    @Test
    fun `reentrant session callback reads suspending preferences`() {
        val stored = InMemoryAppPreferencesStore(messageSound = NotificationSound.ElementDefault)
        val preferences = object : AppPreferencesStore by stored {
            override suspend fun getNotificationSoundChannelConfig(): NotificationSoundChannelConfig =
                withContext(Dispatchers.Unconfined) {
                    yield()
                    stored.getNotificationSoundChannelConfig()
                }
        }
        val executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "notification-session-callback").apply { isDaemon = true }
        }
        try {
            val initialized = executor.submit<NotificationSoundChannelConfig> {
                runBlocking {
                    withContext(Dispatchers.Unconfined) { loadNotificationSoundConfiguration(preferences) }
                }
            }
            assertThat(initialized.get(5, TimeUnit.SECONDS).messageSound).isEqualTo(NotificationSound.ElementDefault)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `legacy migration is idempotent and retains the call ringtone`() {
        val ringtone = NotificationSound.Custom("content://synthetic/ringtone")
        val stored = InMemoryAppPreferencesStore(callRingtone = ringtone, callRingtoneChannelVersion = 4)
        val migrated = loadNotificationSoundConfiguration(stored)
        assertThat(migrated.messageSound).isEqualTo(NotificationSound.ElementDefault)
        assertThat(migrated.messageSoundVersion).isEqualTo(1)
        assertThat(migrated.callRingtone).isEqualTo(ringtone)
        assertThat(migrated.callRingtoneVersion).isEqualTo(4)
        assertThat(loadNotificationSoundConfiguration(stored)).isEqualTo(migrated)
    }

    @Test
    fun `versioned explicit system tone is preserved`() {
        val stored = InMemoryAppPreferencesStore(messageSoundChannelVersion = 2)
        val config = loadNotificationSoundConfiguration(stored)
        assertThat(config.messageSound).isEqualTo(NotificationSound.SystemDefault)
        assertThat(config.messageSoundVersion).isEqualTo(2)
    }
}
