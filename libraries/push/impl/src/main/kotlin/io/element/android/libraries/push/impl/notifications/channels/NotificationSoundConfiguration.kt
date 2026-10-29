/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.push.impl.notifications.channels

import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.NotificationSound
import io.element.android.libraries.preferences.api.store.NotificationSoundChannelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Read the persisted channel versions and preserve the legacy bundled message tone. */
internal fun loadNotificationSoundConfiguration(store: AppPreferencesStore): NotificationSoundChannelConfig = runBlocking(Dispatchers.IO) {
    // Session publication may construct channels inside an active UI flow callback.
    // Give the suspending read its own dispatcher instead of inheriting that event loop.
    val initial = store.getNotificationSoundChannelConfig()
    // SystemDefault meant our bundled tone before channel versions were introduced.
    // Migrate only untouched legacy state, retaining explicit choices and call settings.
    if (initial.messageSoundVersion == 0 && initial.messageSound == NotificationSound.SystemDefault) {
        store.setMessageSoundAndIncrementVersion(NotificationSound.ElementDefault, title = null)
        store.getNotificationSoundChannelConfig()
    } else {
        initial
    }
}
