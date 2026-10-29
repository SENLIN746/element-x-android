/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.notifications.channels

import androidx.core.app.NotificationManagerCompat
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.features.enterprise.test.FakeEnterpriseService
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.NotificationSound
import io.element.android.libraries.preferences.test.InMemoryAppPreferencesStore
import io.element.android.services.toolbox.test.strings.FakeStringProvider
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.robolectric.RuntimeEnvironment

internal fun createNotificationChannels(
    initialize: Boolean = true,
    notificationManager: NotificationManagerCompat = mockk(relaxed = true),
    enterpriseService: EnterpriseService = FakeEnterpriseService(),
    // Default to ElementDefault so the legacy `(SystemDefault, version=0)` migration gate
    // doesn't fire and silently rewrite the prefs store. Tests that exercise the migration
    // construct their own store explicitly.
    appPreferencesStore: AppPreferencesStore = InMemoryAppPreferencesStore(messageSound = NotificationSound.ElementDefault),
) = DefaultNotificationChannels(
    notificationManager = notificationManager,
    stringProvider = FakeStringProvider(),
    context = RuntimeEnvironment.getApplication(),
    enterpriseService = enterpriseService,
    appPreferencesStore = appPreferencesStore,
).also { if (initialize) runBlocking { it.initialize() } }
