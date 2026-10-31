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
import io.element.android.libraries.push.test.notifications.FakeSoundDisplayNameResolver
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NotificationSoundLabelsTest {
    @Test
    fun `present - SystemDefault and Silent display names do not invoke the resolver`() = runTest {
        val resolverCalls = mutableListOf<String>()
        val presenter = createNotificationSettingsPresenter(
            appPreferencesStore = InMemoryAppPreferencesStore(
                messageSound = NotificationSound.SystemDefault,
                callRingtone = NotificationSound.Silent,
            ),
            soundDisplayNameResolver = FakeSoundDisplayNameResolver(
                resolveLambda = { uri ->
                    resolverCalls += uri
                    "Should not appear"
                },
            ),
        )
        presenter.test {
            // The synchronous branches (SystemDefault / Silent) feed the label directly without
            // routing through produceState — so the resolver is never invoked, and the label
            // never flashes from the default to itself.
            consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()
            assertThat(resolverCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - ElementDefault and ElementFade display names do not invoke the resolver`() = runTest {
        val resolverCalls = mutableListOf<String>()
        val presenter = createNotificationSettingsPresenter(
            appPreferencesStore = InMemoryAppPreferencesStore(
                messageSound = NotificationSound.ElementFade,
                callRingtone = NotificationSound.ElementDefault,
            ),
            soundDisplayNameResolver = FakeSoundDisplayNameResolver(
                resolveLambda = { uri ->
                    resolverCalls += uri
                    "Should not appear"
                },
            ),
        )
        presenter.test {
            consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()
            assertThat(resolverCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - resolves Custom display name via SoundDisplayNameResolver`() = runTest {
        val resolverCalls = mutableListOf<String>()
        val presenter = createNotificationSettingsPresenter(
            appPreferencesStore = InMemoryAppPreferencesStore(messageSound = NotificationSound.Custom("content://media/42")),
            soundDisplayNameResolver = FakeSoundDisplayNameResolver(
                resolveLambda = { uri ->
                    resolverCalls += uri
                    "Pixel notification"
                },
            ),
        )
        presenter.test {
            val state = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid &&
                    it.messageSound.displayName == "Pixel notification"
            }.last()
            assertThat(state.messageSound.displayName).isEqualTo("Pixel notification")
            assertThat(resolverCalls).contains("content://media/42")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - Custom display name falls back to localized Custom string when no persisted title and resolver returns null`() = runTest {
        val resolverCalls = mutableListOf<String>()
        val presenter = createNotificationSettingsPresenter(
            // No persisted display name (legacy data path). Resolver returns null. Final fallback
            // is the localised string resource (FakeStringProvider returns "A string" for any
            // resource id) — never the default label, so the row is not mislabelled as "System default".
            appPreferencesStore = InMemoryAppPreferencesStore(messageSound = NotificationSound.Custom("content://gone")),
            soundDisplayNameResolver = FakeSoundDisplayNameResolver(
                resolveLambda = { uri ->
                    resolverCalls += uri
                    null
                },
            ),
        )
        presenter.test {
            val state = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid &&
                    it.messageSound.sound is NotificationSound.Custom &&
                    it.messageSound.displayName == "A string"
            }.last()
            assertThat(state.messageSound.displayName).isEqualTo("A string")
            assertThat(resolverCalls).contains("content://gone")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - persisted in-app pick takes precedence over channel read`() = runTest {
        // When DataStore holds a real in-app pick, the channel read must not run. Persisted
        // value is what the user explicitly chose; channel state is only a fallback.
        var channelReadCalls = 0
        val presenter = createNotificationSettingsPresenter(
            appPreferencesStore = InMemoryAppPreferencesStore(
                messageSound = NotificationSound.Custom("content://persisted"),
                messageSoundDisplayName = "Persisted Tone",
            ),
            notificationSoundUpdater = FakeNotificationSoundUpdater(
                readNoisyChannelSoundLambda = {
                    channelReadCalls++
                    NotificationSound.Custom("content://channel/should-not-appear")
                },
            ),
        )
        presenter.test {
            val state = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid &&
                    it.messageSound.displayName == "Persisted Tone"
            }.last()
            assertThat(state.messageSound.sound).isEqualTo(NotificationSound.Custom("content://persisted"))
            assertThat(channelReadCalls).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - persisted display name takes precedence over live resolver probe`() = runTest {
        val resolverCalls = mutableListOf<String>()
        val presenter = createNotificationSettingsPresenter(
            appPreferencesStore = InMemoryAppPreferencesStore(
                messageSound = NotificationSound.Custom("content://media/42"),
                messageSoundDisplayName = "Captured at copy time",
            ),
            soundDisplayNameResolver = FakeSoundDisplayNameResolver(
                resolveLambda = { uri ->
                    resolverCalls += uri
                    "Live probe (should not appear)"
                },
            ),
        )
        presenter.test {
            val state = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid &&
                    it.messageSound.displayName == "Captured at copy time"
            }.last()
            assertThat(state.messageSound.displayName).isEqualTo("Captured at copy time")
            // Live resolver must not be invoked when a persisted title is available.
            assertThat(resolverCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `present - persisted ElementDefault renders Element default label`() = runTest {
        val presenter = createNotificationSettingsPresenter(
            appPreferencesStore = InMemoryAppPreferencesStore(messageSound = NotificationSound.ElementDefault),
        )
        presenter.test {
            val state = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid &&
                    it.messageSound.sound == NotificationSound.ElementDefault
            }.last()
            // FakeStringProvider returns "A string" for any resource, so this just verifies the
            // ElementDefault branch is taken (vs falling through to the Custom resolver path).
            assertThat(state.messageSound.displayName).isEqualTo("A string")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
