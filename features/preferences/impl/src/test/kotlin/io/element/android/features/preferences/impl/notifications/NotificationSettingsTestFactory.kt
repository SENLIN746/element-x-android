/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.notifications

import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.fullscreenintent.api.FullScreenIntentPermissionsState
import io.element.android.libraries.fullscreenintent.api.aFullScreenIntentPermissionsState
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.notificationsettings.FakeNotificationSettingsService
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.test.InMemoryAppPreferencesStore
import io.element.android.libraries.push.api.PushService
import io.element.android.libraries.push.api.notifications.NotificationSoundUpdater
import io.element.android.libraries.push.api.notifications.sound.NotificationSoundCopier
import io.element.android.libraries.push.test.FakePushService
import io.element.android.libraries.push.test.notifications.FakeNotificationSoundUpdater
import io.element.android.libraries.push.test.notifications.FakeSoundDisplayNameResolver
import io.element.android.libraries.push.test.notifications.sound.FakeNotificationSoundCopier
import io.element.android.libraries.pushstore.test.userpushstore.FakeUserPushStoreFactory
import io.element.android.services.toolbox.test.strings.FakeStringProvider
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope

internal fun TestScope.createNotificationSettingsPresenter(
    notificationSettingsService: FakeNotificationSettingsService = FakeNotificationSettingsService(),
    pushService: PushService = FakePushService(),
    fullScreenIntentPermissionsStateLambda: () -> FullScreenIntentPermissionsState = { aFullScreenIntentPermissionsState() },
    appPreferencesStore: AppPreferencesStore = InMemoryAppPreferencesStore(),
    notificationSoundUpdater: NotificationSoundUpdater = FakeNotificationSoundUpdater(),
    // Default copier passes the picked URI string straight through as the FileProvider URI
    // and uses the URI string as the display name. Tests that exercise copy failures override.
    notificationSoundCopier: NotificationSoundCopier = FakeNotificationSoundCopier(copyLambda = { source, _ ->
        NotificationSoundCopier.CopyResult.Success(source, source)
    }),
    soundDisplayNameResolver: FakeSoundDisplayNameResolver = FakeSoundDisplayNameResolver(),
    featureFlagService: FeatureFlagService = FakeFeatureFlagService(),
): NotificationSettingsPresenter {
    val matrixClient = FakeMatrixClient(notificationSettingsService = notificationSettingsService)
    return NotificationSettingsPresenter(
        notificationSettingsService = notificationSettingsService,
        userPushStoreFactory = FakeUserPushStoreFactory(),
        matrixClient = matrixClient,
        pushService = pushService,
        systemNotificationsEnabledProvider = FakeSystemNotificationsEnabledProvider(),
        fullScreenIntentPermissionsPresenter = { fullScreenIntentPermissionsStateLambda() },
        appPreferencesStore = appPreferencesStore,
        notificationSoundUpdater = notificationSoundUpdater,
        notificationSoundCopier = notificationSoundCopier,
        soundDisplayNameResolver = soundDisplayNameResolver,
        stringProvider = FakeStringProvider(),
        sessionCoroutineScope = backgroundScope,
        featureFlagService = featureFlagService,
        dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
    )
}
