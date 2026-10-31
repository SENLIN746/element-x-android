/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.notifications

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.preferences.api.store.NotificationSound
import io.element.android.libraries.preferences.test.InMemoryAppPreferencesStore
import io.element.android.libraries.push.test.notifications.FakeNotificationSoundUpdater
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import io.element.android.tests.testutils.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NotificationSoundDialogsTest {
    @Test
    fun `present - ShowMessageSoundDialog and DismissMessageSoundDialog toggle dialog visibility`() = runTest {
        val presenter = createNotificationSettingsPresenter()
        presenter.test {
            val initial = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()
            assertThat(initial.showMessageSoundDialog).isFalse()

            initial.eventSink(NotificationSettingsEvent.ShowMessageSoundDialog)
            val shown = consumeItemsUntilPredicate { it.showMessageSoundDialog }.last()
            assertThat(shown.showMessageSoundDialog).isTrue()

            shown.eventSink(NotificationSettingsEvent.DismissMessageSoundDialog)
            val hidden = consumeItemsUntilPredicate { !it.showMessageSoundDialog }.last()
            assertThat(hidden.showMessageSoundDialog).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `present - SelectMessageSoundPreset persists ElementDefault, recreates the channel and closes the dialog`() = runTest {
        val recreateRecorder = lambdaRecorder<NotificationSound, Int, Unit> { _, _ -> }
        val appPreferencesStore = InMemoryAppPreferencesStore()
        val presenter = createNotificationSettingsPresenter(
            appPreferencesStore = appPreferencesStore,
            notificationSoundUpdater = FakeNotificationSoundUpdater(
                recreateNoisyChannelLambda = { sound, version -> recreateRecorder(sound, version) },
            ),
        )
        presenter.test {
            val initial = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()
            initial.eventSink(NotificationSettingsEvent.ShowMessageSoundDialog)
            val shown = consumeItemsUntilPredicate { it.showMessageSoundDialog }.last()

            shown.eventSink(NotificationSettingsEvent.SelectMessageSoundPreset(NotificationSound.ElementDefault))
            advanceUntilIdle()
            consumeItemsUntilPredicate {
                !it.showMessageSoundDialog && it.messageSound.sound == NotificationSound.ElementDefault
            }
            assertThat(appPreferencesStore.getNotificationSoundChannelConfig().messageSound)
                .isEqualTo(NotificationSound.ElementDefault)
            recreateRecorder.assertions().isCalledOnce().with(value(NotificationSound.ElementDefault), value(1))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - LaunchMessageSoundPicker increments the picker token and closes the dialog`() = runTest {
        val presenter = createNotificationSettingsPresenter()
        presenter.test {
            val initial = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()
            assertThat(initial.pendingMessageSoundPickerLaunch).isEqualTo(0)
            initial.eventSink(NotificationSettingsEvent.ShowMessageSoundDialog)
            val shown = consumeItemsUntilPredicate { it.showMessageSoundDialog }.last()

            shown.eventSink(NotificationSettingsEvent.LaunchMessageSoundPicker)
            val launched = consumeItemsUntilPredicate { it.pendingMessageSoundPickerLaunch == 1 }.last()
            assertThat(launched.showMessageSoundDialog).isFalse()
            assertThat(launched.pendingMessageSoundPickerLaunch).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - ShowCallRingtoneDialog and DismissCallRingtoneDialog toggle dialog visibility`() = runTest {
        val presenter = createNotificationSettingsPresenter()
        presenter.test {
            val initial = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()
            assertThat(initial.showCallRingtoneDialog).isFalse()

            initial.eventSink(NotificationSettingsEvent.ShowCallRingtoneDialog)
            val shown = consumeItemsUntilPredicate { it.showCallRingtoneDialog }.last()
            assertThat(shown.showCallRingtoneDialog).isTrue()

            shown.eventSink(NotificationSettingsEvent.DismissCallRingtoneDialog)
            val hidden = consumeItemsUntilPredicate { !it.showCallRingtoneDialog }.last()
            assertThat(hidden.showCallRingtoneDialog).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `present - SelectCallRingtonePreset persists SystemDefault, recreates the channel and closes the dialog`() = runTest {
        val recreateRecorder = lambdaRecorder<NotificationSound, Int, Unit> { _, _ -> }
        val appPreferencesStore = InMemoryAppPreferencesStore(callRingtone = NotificationSound.Silent)
        val presenter = createNotificationSettingsPresenter(
            appPreferencesStore = appPreferencesStore,
            notificationSoundUpdater = FakeNotificationSoundUpdater(
                recreateRingingCallChannelLambda = { sound, version -> recreateRecorder(sound, version) },
            ),
        )
        presenter.test {
            val initial = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid &&
                    it.callRingtone.sound == NotificationSound.Silent
            }.last()
            initial.eventSink(NotificationSettingsEvent.ShowCallRingtoneDialog)
            val shown = consumeItemsUntilPredicate { it.showCallRingtoneDialog }.last()

            shown.eventSink(NotificationSettingsEvent.SelectCallRingtonePreset(NotificationSound.SystemDefault))
            advanceUntilIdle()
            consumeItemsUntilPredicate {
                !it.showCallRingtoneDialog && it.callRingtone.sound == NotificationSound.SystemDefault
            }
            assertThat(appPreferencesStore.getNotificationSoundChannelConfig().callRingtone)
                .isEqualTo(NotificationSound.SystemDefault)
            recreateRecorder.assertions().isCalledOnce().with(value(NotificationSound.SystemDefault), value(1))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - LaunchCallRingtonePicker increments the picker token and closes the dialog`() = runTest {
        val presenter = createNotificationSettingsPresenter()
        presenter.test {
            val initial = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()
            assertThat(initial.pendingCallRingtonePickerLaunch).isEqualTo(0)
            initial.eventSink(NotificationSettingsEvent.ShowCallRingtoneDialog)
            val shown = consumeItemsUntilPredicate { it.showCallRingtoneDialog }.last()

            shown.eventSink(NotificationSettingsEvent.LaunchCallRingtonePicker)
            val launched = consumeItemsUntilPredicate { it.pendingCallRingtonePickerLaunch == 1 }.last()
            assertThat(launched.showCallRingtoneDialog).isFalse()
            assertThat(launched.pendingCallRingtonePickerLaunch).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `present - SelectCallRingtonePreset clears legacy ringtone classification`() = runTest {
        // Legacy state: persisted SystemDefault + channel version 0 means the presenter probes the
        // channel and surfaces whatever Android system has. We seed that probe with Silent so the
        // initial row reads Silent even though DataStore holds SystemDefault. A preset pick must
        // null the legacy classifier; otherwise re-reading state would still resolve to Silent
        // after the SystemDefault selection persists.
        val appPreferencesStore = InMemoryAppPreferencesStore(
            callRingtone = NotificationSound.SystemDefault,
            callRingtoneChannelVersion = 0,
        )
        val presenter = createNotificationSettingsPresenter(
            appPreferencesStore = appPreferencesStore,
            notificationSoundUpdater = FakeNotificationSoundUpdater(
                recreateRingingCallChannelLambda = { _, _ -> },
                readRingingCallChannelSoundLambda = { NotificationSound.Silent },
            ),
        )
        presenter.test {
            val initial = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid &&
                    it.callRingtone.sound == NotificationSound.Silent
            }.last()
            initial.eventSink(NotificationSettingsEvent.SelectCallRingtonePreset(NotificationSound.SystemDefault))
            advanceUntilIdle()
            val after = consumeItemsUntilPredicate {
                it.callRingtone.sound == NotificationSound.SystemDefault
            }.last()
            assertThat(after.callRingtone.sound).isEqualTo(NotificationSound.SystemDefault)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
