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
import io.element.android.libraries.push.api.notifications.sound.NotificationSoundCopier
import io.element.android.libraries.push.test.notifications.FakeNotificationSoundUpdater
import io.element.android.libraries.push.test.notifications.sound.FakeNotificationSoundCopier
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import io.element.android.tests.testutils.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NotificationSoundCopyTest {
    @Test
    fun `present - SetMessageSound with Custom copies via copier and persists the FileProvider URI`() = runTest {
        val recreateRecorder = lambdaRecorder<NotificationSound, Int, Unit> { _, _ -> }
        val appPreferencesStore = InMemoryAppPreferencesStore()
        val presenter = createNotificationSettingsPresenter(
            appPreferencesStore = appPreferencesStore,
            notificationSoundUpdater = FakeNotificationSoundUpdater(
                recreateNoisyChannelLambda = { sound, version -> recreateRecorder(sound, version) },
            ),
            notificationSoundCopier = FakeNotificationSoundCopier(copyLambda = { _, _ ->
                NotificationSoundCopier.CopyResult.Success(
                    fileProviderUriString = "content://my.app.fileprovider/notification_sounds/message_sound.ogg",
                    displayName = "Cool Tone",
                )
            }),
        )
        presenter.test {
            val initialState = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()
            initialState.eventSink(NotificationSettingsEvent.SetMessageSound(NotificationSound.Custom("content://media/source")))
            advanceUntilIdle()

            val expectedSound = NotificationSound.Custom("content://my.app.fileprovider/notification_sounds/message_sound.ogg")
            assertThat(appPreferencesStore.getNotificationSoundChannelConfig().messageSound).isEqualTo(expectedSound)
            assertThat(appPreferencesStore.getMessageSoundDisplayNameFlow().first()).isEqualTo("Cool Tone")
            recreateRecorder.assertions().isCalledOnce().with(value(expectedSound), value(1))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - SetMessageSound copy failure surfaces inline error and leaves prior choice intact`() = runTest {
        val priorChoice = NotificationSound.Custom("content://prior")
        val appPreferencesStore = InMemoryAppPreferencesStore(messageSound = priorChoice)
        val presenter = createNotificationSettingsPresenter(
            appPreferencesStore = appPreferencesStore,
            notificationSoundCopier = FakeNotificationSoundCopier(copyLambda = { _, _ ->
                NotificationSoundCopier.CopyResult.UnplayableSource
            }),
        )
        presenter.test {
            val initialState = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()
            initialState.eventSink(NotificationSettingsEvent.SetMessageSound(NotificationSound.Custom("content://broken")))

            val errorState = consumeItemsUntilPredicate { it.messageSound.copyError }.last()
            assertThat(errorState.messageSound.copyError).isTrue()
            // Persisted state is unchanged.
            assertThat(appPreferencesStore.getNotificationSoundChannelConfig().messageSound).isEqualTo(priorChoice)

            // Dismissing the inline error clears the flag without altering persistence.
            errorState.eventSink(NotificationSettingsEvent.DismissMessageSoundCopyError)
            val cleared = consumeItemsUntilPredicate { !it.messageSound.copyError }.last()
            assertThat(cleared.messageSound.copyError).isFalse()
            assertThat(appPreferencesStore.getNotificationSoundChannelConfig().messageSound).isEqualTo(priorChoice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - SetCallRingtone copy failure surfaces inline error and leaves prior choice intact`() = runTest {
        val priorChoice = NotificationSound.Custom("content://prior")
        val appPreferencesStore = InMemoryAppPreferencesStore(callRingtone = priorChoice)
        val presenter = createNotificationSettingsPresenter(
            appPreferencesStore = appPreferencesStore,
            notificationSoundCopier = FakeNotificationSoundCopier(copyLambda = { _, _ ->
                NotificationSoundCopier.CopyResult.UnplayableSource
            }),
        )
        presenter.test {
            val initialState = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()
            initialState.eventSink(NotificationSettingsEvent.SetCallRingtone(NotificationSound.Custom("content://broken")))

            val errorState = consumeItemsUntilPredicate { it.callRingtone.copyError }.last()
            assertThat(errorState.callRingtone.copyError).isTrue()
            // Persisted state is unchanged.
            assertThat(appPreferencesStore.getNotificationSoundChannelConfig().callRingtone).isEqualTo(priorChoice)

            // Dismissing the inline error clears the flag without altering persistence.
            errorState.eventSink(NotificationSettingsEvent.DismissCallRingtoneCopyError)
            val cleared = consumeItemsUntilPredicate { !it.callRingtone.copyError }.last()
            assertThat(cleared.callRingtone.copyError).isFalse()
            assertThat(appPreferencesStore.getNotificationSoundChannelConfig().callRingtone).isEqualTo(priorChoice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `present - SystemDefault and Silent picks bypass the copier`() = runTest {
        val copierCalls = mutableListOf<String>()
        val presenter = createNotificationSettingsPresenter(
            notificationSoundUpdater = FakeNotificationSoundUpdater(
                recreateNoisyChannelLambda = { _, _ -> },
                recreateRingingCallChannelLambda = { _, _ -> },
            ),
            notificationSoundCopier = FakeNotificationSoundCopier(copyLambda = { source, _ ->
                copierCalls += source
                NotificationSoundCopier.CopyResult.Success(source, "x")
            }),
        )
        presenter.test {
            val initialState = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()
            initialState.eventSink(NotificationSettingsEvent.SetMessageSound(NotificationSound.SystemDefault))
            initialState.eventSink(NotificationSettingsEvent.SetCallRingtone(NotificationSound.Silent))
            advanceUntilIdle()
            assertThat(copierCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `present - non-Custom picks delete the stored slot file`() = runTest {
        val deleteCalls = mutableListOf<NotificationSoundCopier.SoundSlot>()
        // Start from persisted Custom for both slots so the non-Custom picks below are real
        // transitions (no-op short-circuit only fires when the new sound equals the persisted one).
        val presenter = createNotificationSettingsPresenter(
            appPreferencesStore = InMemoryAppPreferencesStore(
                messageSound = NotificationSound.Custom("content://prior-message"),
                callRingtone = NotificationSound.Custom("content://prior-call"),
            ),
            notificationSoundUpdater = FakeNotificationSoundUpdater(
                recreateNoisyChannelLambda = { _, _ -> },
                recreateRingingCallChannelLambda = { _, _ -> },
            ),
            notificationSoundCopier = FakeNotificationSoundCopier(
                copyLambda = { _, _ -> NotificationSoundCopier.CopyResult.Failure(IllegalStateException("not expected")) },
                deleteStoredSoundForLambda = { slot -> deleteCalls += slot },
            ),
        )
        presenter.test {
            val initialState = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()
            initialState.eventSink(NotificationSettingsEvent.SetMessageSound(NotificationSound.SystemDefault))
            initialState.eventSink(NotificationSettingsEvent.SetCallRingtone(NotificationSound.Silent))
            advanceUntilIdle()
            assertThat(deleteCalls).containsExactly(
                NotificationSoundCopier.SoundSlot.Message,
                NotificationSoundCopier.SoundSlot.Call,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `present - re-selecting the persisted non-Custom sound is a no-op`() = runTest {
        val recreateCalls = mutableListOf<Int>()
        val deleteCalls = mutableListOf<NotificationSoundCopier.SoundSlot>()
        val copierCalls = mutableListOf<String>()
        val appPreferencesStore = InMemoryAppPreferencesStore(
            messageSound = NotificationSound.ElementDefault,
            messageSoundChannelVersion = 5,
            callRingtone = NotificationSound.Silent,
            callRingtoneChannelVersion = 3,
        )
        val presenter = createNotificationSettingsPresenter(
            appPreferencesStore = appPreferencesStore,
            notificationSoundCopier = FakeNotificationSoundCopier(
                copyLambda = { source, _ ->
                    copierCalls += source
                    NotificationSoundCopier.CopyResult.Success(source, "title")
                },
                deleteStoredSoundForLambda = { slot -> deleteCalls += slot },
            ),
            notificationSoundUpdater = FakeNotificationSoundUpdater(
                recreateNoisyChannelLambda = { _, version -> recreateCalls += version },
                recreateRingingCallChannelLambda = { _, version -> recreateCalls += version },
            ),
        )
        presenter.test {
            val initial = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()

            // Re-pick the persisted sound for both slots — neither should churn the channel,
            // bump the version, or hit the copier / delete sweep.
            initial.eventSink(NotificationSettingsEvent.SelectMessageSoundPreset(NotificationSound.ElementDefault))
            initial.eventSink(NotificationSettingsEvent.SetCallRingtone(NotificationSound.Silent))
            advanceUntilIdle()

            val config = appPreferencesStore.getNotificationSoundChannelConfig()
            assertThat(config.messageSoundVersion).isEqualTo(5)
            assertThat(config.callRingtoneVersion).isEqualTo(3)
            assertThat(recreateCalls).isEmpty()
            assertThat(copierCalls).isEmpty()
            assertThat(deleteCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `present - rapid back-to-back picks settle in deterministic order`() = runTest {
        // Defends the persist+recreate window against rapid event firing (e.g. an accessibility
        // service driving the dialog). The per-slot mutex in the presenter forces copy → persist
        // → recreate to complete for the first pick before the second begins, so the recreated
        // version sequence matches the persisted version sequence.
        val recreatedVersions = mutableListOf<Int>()
        val copyOrder = mutableListOf<String>()
        val appPreferencesStore = InMemoryAppPreferencesStore()
        val presenter = createNotificationSettingsPresenter(
            appPreferencesStore = appPreferencesStore,
            notificationSoundCopier = FakeNotificationSoundCopier(copyLambda = { source, _ ->
                copyOrder += source
                NotificationSoundCopier.CopyResult.Success(source, "title")
            }),
            notificationSoundUpdater = FakeNotificationSoundUpdater(
                recreateNoisyChannelLambda = { _, version -> recreatedVersions += version },
            ),
        )
        presenter.test {
            val initial = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()
            initial.eventSink(NotificationSettingsEvent.SetMessageSound(NotificationSound.Custom("content://A")))
            initial.eventSink(NotificationSettingsEvent.SetMessageSound(NotificationSound.Custom("content://B")))
            advanceUntilIdle()

            assertThat(copyOrder).containsExactly("content://A", "content://B").inOrder()
            assertThat(recreatedVersions).containsExactly(1, 2).inOrder()
            val config = appPreferencesStore.getNotificationSoundChannelConfig()
            assertThat(config.messageSoundVersion).isEqualTo(2)
            assertThat(config.messageSound).isEqualTo(NotificationSound.Custom("content://B"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `present - Custom pick does not invoke deleteStoredSoundFor (copier sweeps inline)`() = runTest {
        val deleteCalls = mutableListOf<NotificationSoundCopier.SoundSlot>()
        val presenter = createNotificationSettingsPresenter(
            notificationSoundUpdater = FakeNotificationSoundUpdater(
                recreateNoisyChannelLambda = { _, _ -> },
            ),
            notificationSoundCopier = FakeNotificationSoundCopier(
                copyLambda = { _, _ ->
                    NotificationSoundCopier.CopyResult.Success(
                        fileProviderUriString = "content://my.app.fileprovider/notification_sounds/message_sound.mp3",
                        displayName = "New Tone",
                    )
                },
                deleteStoredSoundForLambda = { slot -> deleteCalls += slot },
            ),
        )
        presenter.test {
            val initialState = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()
            initialState.eventSink(NotificationSettingsEvent.SetMessageSound(NotificationSound.Custom("content://media/source")))
            advanceUntilIdle()
            assertThat(deleteCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
