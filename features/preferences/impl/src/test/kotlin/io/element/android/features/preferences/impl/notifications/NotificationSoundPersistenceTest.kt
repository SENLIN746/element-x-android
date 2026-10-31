/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.notifications

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.NotificationSound
import io.element.android.libraries.preferences.test.InMemoryAppPreferencesStore
import io.element.android.libraries.push.test.notifications.FakeNotificationSoundUpdater
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NotificationSoundPersistenceTest {
    @Test
    fun `present - SetMessageSound atomically persists, increments version, and recreates channel`() = runTest {
        val recreateRecorder = lambdaRecorder<NotificationSound, Int, Unit> { _, _ -> }
        val appPreferencesStore = InMemoryAppPreferencesStore()
        val customSound = NotificationSound.Custom("content://media/internal/audio/media/42")
        val presenter = createNotificationSettingsPresenter(
            appPreferencesStore = appPreferencesStore,
            notificationSoundUpdater = FakeNotificationSoundUpdater(
                recreateNoisyChannelLambda = { sound, version -> recreateRecorder(sound, version) },
            ),
        )
        presenter.test {
            val initialState = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()
            assertThat(initialState.messageSound.sound).isEqualTo(NotificationSound.SystemDefault)

            initialState.eventSink(NotificationSettingsEvent.SetMessageSound(customSound))

            val updatedState = consumeItemsUntilPredicate { it.messageSound.sound == customSound }.last()
            assertThat(updatedState.messageSound.sound).isEqualTo(customSound)
            assertThat(appPreferencesStore.getNotificationSoundChannelConfig().messageSoundVersion).isEqualTo(1)
            recreateRecorder.assertions().isCalledOnce().with(value(customSound), value(1))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - SetCallRingtone with Silent atomically persists and recreates channel`() = runTest {
        val recreateRecorder = lambdaRecorder<NotificationSound, Int, Unit> { _, _ -> }
        val appPreferencesStore = InMemoryAppPreferencesStore()
        val presenter = createNotificationSettingsPresenter(
            appPreferencesStore = appPreferencesStore,
            notificationSoundUpdater = FakeNotificationSoundUpdater(
                recreateRingingCallChannelLambda = { sound, version -> recreateRecorder(sound, version) },
            ),
        )
        presenter.test {
            val initialState = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()
            assertThat(initialState.callRingtone.sound).isEqualTo(NotificationSound.SystemDefault)

            initialState.eventSink(NotificationSettingsEvent.SetCallRingtone(NotificationSound.Silent))

            val updatedState = consumeItemsUntilPredicate { it.callRingtone.sound == NotificationSound.Silent }.last()
            assertThat(updatedState.callRingtone.sound).isEqualTo(NotificationSound.Silent)
            assertThat(appPreferencesStore.getNotificationSoundChannelConfig().callRingtoneVersion).isEqualTo(1)
            recreateRecorder.assertions().isCalledOnce().with(value(NotificationSound.Silent), value(1))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - successive message sound changes increment version each time`() = runTest {
        val versions = mutableListOf<Int>()
        val appPreferencesStore = InMemoryAppPreferencesStore()
        val presenter = createNotificationSettingsPresenter(
            appPreferencesStore = appPreferencesStore,
            notificationSoundUpdater = FakeNotificationSoundUpdater(
                recreateNoisyChannelLambda = { _, version -> versions += version },
            ),
        )
        presenter.test {
            val initialState = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()

            initialState.eventSink(NotificationSettingsEvent.SetMessageSound(NotificationSound.Custom("content://a")))
            consumeItemsUntilPredicate { it.messageSound.sound == NotificationSound.Custom("content://a") }
            initialState.eventSink(NotificationSettingsEvent.SetMessageSound(NotificationSound.Custom("content://b")))
            consumeItemsUntilPredicate { it.messageSound.sound == NotificationSound.Custom("content://b") }
            initialState.eventSink(NotificationSettingsEvent.SetMessageSound(NotificationSound.SystemDefault))
            consumeItemsUntilPredicate { it.messageSound.sound == NotificationSound.SystemDefault }

            assertThat(versions).containsExactly(1, 2, 3).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - SetMessageSound with SystemDefault clears existing custom URI`() = runTest {
        val recreateRecorder = lambdaRecorder<NotificationSound, Int, Unit> { _, _ -> }
        val appPreferencesStore = InMemoryAppPreferencesStore(messageSound = NotificationSound.Custom("content://existing"))
        val presenter = createNotificationSettingsPresenter(
            appPreferencesStore = appPreferencesStore,
            notificationSoundUpdater = FakeNotificationSoundUpdater(
                recreateNoisyChannelLambda = { sound, version -> recreateRecorder(sound, version) },
            ),
        )
        presenter.test {
            consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid &&
                    it.messageSound.sound == NotificationSound.Custom("content://existing")
            }.last().eventSink(NotificationSettingsEvent.SetMessageSound(NotificationSound.SystemDefault))
            consumeItemsUntilPredicate { it.messageSound.sound == NotificationSound.SystemDefault }
            assertThat(appPreferencesStore.getNotificationSoundChannelConfig().messageSound).isEqualTo(NotificationSound.SystemDefault)
            recreateRecorder.assertions().isCalledOnce().with(value(NotificationSound.SystemDefault), value(1))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - SetMessageSound surfaces DataStore failure via changeNotificationSettingAction`() = runTest {
        val backing = InMemoryAppPreferencesStore()
        val store = object : AppPreferencesStore by backing {
            override suspend fun setMessageSoundAndIncrementVersion(sound: NotificationSound, title: String?): Int {
                error("Simulated DataStore failure")
            }
        }
        val presenter = createNotificationSettingsPresenter(appPreferencesStore = store)
        presenter.test {
            val initialState = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()

            initialState.eventSink(NotificationSettingsEvent.SetMessageSound(NotificationSound.Custom("content://x")))

            val errorState = consumeItemsUntilPredicate {
                it.changeNotificationSettingAction.isFailure()
            }.last()
            assertThat(errorState.changeNotificationSettingAction.isFailure()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
