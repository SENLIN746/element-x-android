/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.push.impl.notifications.channels

import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.NotificationSound
import io.element.android.libraries.preferences.api.store.NotificationSoundChannelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read the persisted channel versions and preserve the legacy bundled message tone. */
internal suspend fun loadNotificationSoundConfiguration(store: AppPreferencesStore): NotificationSoundChannelConfig = withContext(Dispatchers.IO) {
    // Preference IO suspends; constructors and session flow callbacks never wait on a blocking bridge.
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
