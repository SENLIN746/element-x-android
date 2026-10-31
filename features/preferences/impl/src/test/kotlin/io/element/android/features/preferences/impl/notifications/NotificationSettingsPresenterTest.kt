/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.notifications

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.featureflag.api.ShowAllActivityInRoomListFeature
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.fullscreenintent.api.aFullScreenIntentPermissionsState
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.room.RoomNotificationMode
import io.element.android.libraries.matrix.test.AN_EXCEPTION
import io.element.android.libraries.matrix.test.notificationsettings.FakeNotificationSettingsService
import io.element.android.libraries.push.test.FakePushService
import io.element.android.libraries.pushproviders.test.FakePushProvider
import io.element.android.tests.testutils.awaitLastSequentialItem
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class NotificationSettingsPresenterTest {
    @Test
    fun `present - ensures initial state is correct`() = runTest {
        val presenter = createNotificationSettingsPresenter()
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.appSettings.appNotificationsEnabled).isFalse()
            assertThat(initialState.appSettings.systemNotificationsEnabled).isTrue()
            assertThat(initialState.matrixSettings).isEqualTo(NotificationSettingsState.MatrixSettings.Uninitialized)
            val loadedState = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()
            assertThat(loadedState.appSettings.appNotificationsEnabled).isTrue()
            assertThat(loadedState.appSettings.systemNotificationsEnabled).isTrue()
            val valid = loadedState.matrixSettings as? NotificationSettingsState.MatrixSettings.Valid
            assertThat(valid?.atRoomNotificationsEnabled).isFalse()
            assertThat(valid?.callNotificationsEnabled).isFalse()
            assertThat(valid?.inviteForMeNotificationsEnabled).isFalse()
            assertThat(valid?.defaultGroupNotificationMode).isEqualTo(RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY)
            assertThat(valid?.defaultOneToOneNotificationMode).isEqualTo(RoomNotificationMode.ALL_MESSAGES)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - default group notification mode changed`() = runTest {
        val notificationSettingsService = FakeNotificationSettingsService()
        val presenter = createNotificationSettingsPresenter(notificationSettingsService)
        presenter.test {
            notificationSettingsService.setDefaultRoomNotificationMode(isEncrypted = true, isDM = false, mode = RoomNotificationMode.ALL_MESSAGES)
            notificationSettingsService.setDefaultRoomNotificationMode(isEncrypted = false, isDM = false, mode = RoomNotificationMode.ALL_MESSAGES)
            val updatedState = consumeItemsUntilPredicate {
                (it.matrixSettings as? NotificationSettingsState.MatrixSettings.Valid)
                    ?.defaultGroupNotificationMode == RoomNotificationMode.ALL_MESSAGES
            }.last()
            val valid = updatedState.matrixSettings as? NotificationSettingsState.MatrixSettings.Valid
            assertThat(valid?.defaultGroupNotificationMode).isEqualTo(RoomNotificationMode.ALL_MESSAGES)
        }
    }

    @Test
    fun `present - notification settings mismatched`() = runTest {
        val notificationSettingsService = FakeNotificationSettingsService()
        val presenter = createNotificationSettingsPresenter(notificationSettingsService)
        presenter.test {
            notificationSettingsService.setDefaultRoomNotificationMode(
                isEncrypted = true,
                isDM = false,
                mode = RoomNotificationMode.ALL_MESSAGES
            )
            notificationSettingsService.setDefaultRoomNotificationMode(
                isEncrypted = false,
                isDM = false,
                mode = RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY
            )
            val updatedState = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Invalid
            }.last()
            assertThat(updatedState.matrixSettings).isEqualTo(NotificationSettingsState.MatrixSettings.Invalid(fixFailed = false))
        }
    }

    @Test
    fun `present - fix notification settings mismatched`() = runTest {
        // Start with a mismatched configuration
        val notificationSettingsService = FakeNotificationSettingsService(
            initialEncryptedGroupDefaultMode = RoomNotificationMode.ALL_MESSAGES,
            initialGroupDefaultMode = RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY,
            initialEncryptedOneToOneDefaultMode = RoomNotificationMode.ALL_MESSAGES,
            initialOneToOneDefaultMode = RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY
        )
        val presenter = createNotificationSettingsPresenter(notificationSettingsService)
        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(NotificationSettingsEvent.FixConfigurationMismatch)
            val fixedState = consumeItemsUntilPredicate(timeout = 2000.milliseconds) {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()
            val fixedMatrixState = fixedState.matrixSettings as? NotificationSettingsState.MatrixSettings.Valid
            assertThat(fixedMatrixState?.defaultGroupNotificationMode).isEqualTo(RoomNotificationMode.ALL_MESSAGES)
        }
    }

    @Test
    fun `present - set notifications enabled`() = runTest {
        val unregisterWithResult = lambdaRecorder<MatrixClient, Result<Unit>> { Result.success(Unit) }
        val ensurePusherIsRegisteredResult = lambdaRecorder<Result<Unit>> { Result.success(Unit) }
        val presenter = createNotificationSettingsPresenter(
            pushService = FakePushService(
                currentPushProvider = {
                    FakePushProvider(
                        unregisterWithResult = unregisterWithResult,
                    )
                },
                ensurePusherIsRegisteredResult = ensurePusherIsRegisteredResult,
            )
        )
        presenter.test {
            val loadedState = consumeItemsUntilPredicate {
                it.matrixSettings is NotificationSettingsState.MatrixSettings.Valid
            }.last()
            assertThat(loadedState.appSettings.appNotificationsEnabled).isTrue()
            loadedState.eventSink(NotificationSettingsEvent.SetNotificationsEnabled(false))
            val updatedState = consumeItemsUntilPredicate {
                !it.appSettings.appNotificationsEnabled
            }.last()
            assertThat(updatedState.appSettings.appNotificationsEnabled).isFalse()
            unregisterWithResult.assertions().isCalledOnce()
            // Enable notification again
            loadedState.eventSink(NotificationSettingsEvent.SetNotificationsEnabled(true))
            val updatedState2 = consumeItemsUntilPredicate {
                it.appSettings.appNotificationsEnabled
            }.last()
            assertThat(updatedState2.appSettings.appNotificationsEnabled).isTrue()
            ensurePusherIsRegisteredResult.assertions().isNeverCalled()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - set call notifications enabled`() = runTest {
        val presenter = createNotificationSettingsPresenter()
        presenter.test {
            val loadedState = consumeItemsUntilPredicate {
                (it.matrixSettings as? NotificationSettingsState.MatrixSettings.Valid)?.callNotificationsEnabled == false
            }.last()
            val validMatrixState = loadedState.matrixSettings as? NotificationSettingsState.MatrixSettings.Valid
            assertThat(validMatrixState?.callNotificationsEnabled).isFalse()
            loadedState.eventSink(NotificationSettingsEvent.SetCallNotificationsEnabled(true))
            val updatedState = consumeItemsUntilPredicate {
                (it.matrixSettings as? NotificationSettingsState.MatrixSettings.Valid)?.callNotificationsEnabled == true
            }.last()
            val updatedMatrixState = updatedState.matrixSettings as? NotificationSettingsState.MatrixSettings.Valid
            assertThat(updatedMatrixState?.callNotificationsEnabled).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - set invite for me notifications enabled`() = runTest {
        val presenter = createNotificationSettingsPresenter()
        presenter.test {
            val loadedState = consumeItemsUntilPredicate {
                (it.matrixSettings as? NotificationSettingsState.MatrixSettings.Valid)?.inviteForMeNotificationsEnabled == false
            }.last()
            val validMatrixState = loadedState.matrixSettings as? NotificationSettingsState.MatrixSettings.Valid
            assertThat(validMatrixState?.inviteForMeNotificationsEnabled).isFalse()
            loadedState.eventSink(NotificationSettingsEvent.SetInviteForMeNotificationsEnabled(true))
            val updatedState = consumeItemsUntilPredicate {
                (it.matrixSettings as? NotificationSettingsState.MatrixSettings.Valid)?.inviteForMeNotificationsEnabled == true
            }.last()
            val updatedMatrixState = updatedState.matrixSettings as? NotificationSettingsState.MatrixSettings.Valid
            assertThat(updatedMatrixState?.inviteForMeNotificationsEnabled).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - set atRoom notifications enabled`() = runTest {
        val presenter = createNotificationSettingsPresenter()
        presenter.test {
            val loadedState = consumeItemsUntilPredicate {
                (it.matrixSettings as? NotificationSettingsState.MatrixSettings.Valid)?.atRoomNotificationsEnabled == false
            }.last()
            val validMatrixState = loadedState.matrixSettings as? NotificationSettingsState.MatrixSettings.Valid
            assertThat(validMatrixState?.atRoomNotificationsEnabled).isFalse()
            loadedState.eventSink(NotificationSettingsEvent.SetAtRoomNotificationsEnabled(true))
            val updatedState = consumeItemsUntilPredicate {
                (it.matrixSettings as? NotificationSettingsState.MatrixSettings.Valid)?.atRoomNotificationsEnabled == true
            }.last()
            val updatedMatrixState = updatedState.matrixSettings as? NotificationSettingsState.MatrixSettings.Valid
            assertThat(updatedMatrixState?.atRoomNotificationsEnabled).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - clear notification settings change error`() = runTest {
        val notificationSettingsService = FakeNotificationSettingsService()
        val presenter = createNotificationSettingsPresenter(notificationSettingsService)
        notificationSettingsService.givenSetAtRoomError(AN_EXCEPTION)
        presenter.test {
            val loadedState = consumeItemsUntilPredicate {
                (it.matrixSettings as? NotificationSettingsState.MatrixSettings.Valid)?.atRoomNotificationsEnabled == false
            }.last()
            val validMatrixState = loadedState.matrixSettings as? NotificationSettingsState.MatrixSettings.Valid
            assertThat(validMatrixState?.atRoomNotificationsEnabled).isFalse()
            loadedState.eventSink(NotificationSettingsEvent.SetAtRoomNotificationsEnabled(true))
            val errorState = consumeItemsUntilPredicate {
                it.changeNotificationSettingAction.isFailure()
            }.last()
            assertThat(errorState.changeNotificationSettingAction.isFailure()).isTrue()
            errorState.eventSink(NotificationSettingsEvent.ClearNotificationChangeError)
            val clearErrorState = consumeItemsUntilPredicate {
                it.changeNotificationSettingAction.isUninitialized()
            }.last()
            assertThat(clearErrorState.changeNotificationSettingAction.isUninitialized()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - RefreshSystemNotificationsEnabled also refreshes fullScreenIntentState`() = runTest {
        var lambdaResult = aFullScreenIntentPermissionsState(permissionGranted = false)
        val fullScreenIntentPermissionsStateLambda = { lambdaResult }
        val presenter = createNotificationSettingsPresenter(
            pushService = FakePushService(),
            fullScreenIntentPermissionsStateLambda = fullScreenIntentPermissionsStateLambda,
        )
        presenter.test {
            val initialState = awaitLastSequentialItem()
            assertThat(initialState.fullScreenIntentPermissionsState.permissionGranted).isFalse()

            // Change the notification settings
            lambdaResult = lambdaResult.copy(permissionGranted = true)
            // Check it's not changed unless we refresh
            expectNoEvents()

            // Refresh
            initialState.eventSink.invoke(NotificationSettingsEvent.RefreshSystemNotificationsEnabled)
            val refreshed = consumeItemsUntilPredicate { it.fullScreenIntentPermissionsState.permissionGranted }.last()
            assertThat(refreshed.fullScreenIntentPermissionsState.permissionGranted).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - showAllActivityInRoomList reflects the feature flag value`() = runTest {
        val featureFlagService = FakeFeatureFlagService(
            initialState = mapOf(ShowAllActivityInRoomListFeature.key to true)
        )
        val presenter = createNotificationSettingsPresenter(featureFlagService = featureFlagService)
        presenter.test {
            val state = consumeItemsUntilPredicate { it.showAllActivityInRoomList }.last()
            assertThat(state.showAllActivityInRoomList).isTrue()
            featureFlagService.setFeatureEnabled(ShowAllActivityInRoomListFeature, false)

            val updatedState = consumeItemsUntilPredicate { !it.showAllActivityInRoomList }.last()
            assertThat(updatedState.showAllActivityInRoomList).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - ToggleShowAllActivityInRoomList toggles the feature flag`() = runTest {
        val featureFlagService = FakeFeatureFlagService(
            initialState = mapOf(ShowAllActivityInRoomListFeature.key to false)
        )
        val presenter = createNotificationSettingsPresenter(featureFlagService = featureFlagService)
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.showAllActivityInRoomList).isFalse()
            initialState.eventSink(NotificationSettingsEvent.ToggleShowAllActivityInRoomList(true))

            val enabledState = consumeItemsUntilPredicate { it.showAllActivityInRoomList }.last()
            assertThat(enabledState.showAllActivityInRoomList).isTrue()
            assertThat(featureFlagService.isFeatureEnabled(ShowAllActivityInRoomListFeature)).isTrue()
            enabledState.eventSink(NotificationSettingsEvent.ToggleShowAllActivityInRoomList(false))

            val disabledState = consumeItemsUntilPredicate { !it.showAllActivityInRoomList }.last()
            assertThat(disabledState.showAllActivityInRoomList).isFalse()
            assertThat(featureFlagService.isFeatureEnabled(ShowAllActivityInRoomListFeature)).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
