/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
package io.element.android.features.preferences.impl.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.NotificationSound
import io.element.android.libraries.push.api.notifications.NotificationSoundUpdater
import io.element.android.libraries.push.api.notifications.SoundDisplayNameResolver
import io.element.android.libraries.push.api.notifications.sound.NotificationSoundCopier
import io.element.android.libraries.push.api.notifications.sound.NotificationSoundCopier.CopyResult
import io.element.android.libraries.push.api.notifications.sound.NotificationSoundCopier.SoundSlot
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/** Sound copy, preference persistence and channel replacement share one serialized lifecycle. */
internal class NotificationSoundSettingsActions(
    private val appPreferencesStore: AppPreferencesStore,
    private val notificationSoundUpdater: NotificationSoundUpdater,
    private val notificationSoundCopier: NotificationSoundCopier,
    private val soundDisplayNameResolver: SoundDisplayNameResolver,
    private val stringProvider: StringProvider,
) {
    private val messageSoundLock = Mutex()
    private val callRingtoneLock = Mutex()

    fun applyMessageSound(
        sound: NotificationSound,
        sessionCoroutineScope: CoroutineScope,
        onCopyError: () -> Unit,
        onCopySuccess: () -> Unit,
        onChannelFailure: (Throwable) -> Unit,
    ) {
        sessionCoroutineScope.launch {
            messageSoundLock.withLock {
                // Re-selecting the current non-Custom sound is a no-op: skip the channel churn
                // (delete + create round-trips through system_server) and the version bump. Custom
                // still falls through so the copier refreshes the file from the (possibly updated)
                // source URI.
                if (sound !is NotificationSound.Custom && appPreferencesStore.getMessageSoundFlow().first() == sound) {
                    onCopySuccess()
                    return@withLock
                }
                val resolved = resolvePickedSound(sound, SoundSlot.Message, onCopyError) ?: return@withLock
                runCatchingExceptions {
                    onCopySuccess()
                    val newVersion = appPreferencesStore.setMessageSoundAndIncrementVersion(resolved.first, resolved.second)
                    notificationSoundUpdater.recreateNoisyChannel(resolved.first, newVersion)
                    // Non-Custom picks bypass the copier, so they don't sweep the prior Custom file
                    // inline. Drop it now that the new channel no longer references it.
                    if (resolved.first !is NotificationSound.Custom) {
                        notificationSoundCopier.deleteStoredSoundFor(SoundSlot.Message)
                    }
                }.onFailure(onChannelFailure)
            }
        }
    }

    fun applyCallRingtone(
        sound: NotificationSound,
        sessionCoroutineScope: CoroutineScope,
        onCopyError: () -> Unit,
        onCopySuccess: () -> Unit,
        onChannelFailure: (Throwable) -> Unit,
    ) {
        sessionCoroutineScope.launch {
            callRingtoneLock.withLock {
                if (sound !is NotificationSound.Custom && appPreferencesStore.getCallRingtoneFlow().first() == sound) {
                    onCopySuccess()
                    return@withLock
                }
                val resolved = resolvePickedSound(sound, SoundSlot.Call, onCopyError) ?: return@withLock
                runCatchingExceptions {
                    onCopySuccess()
                    val newVersion = appPreferencesStore.setCallRingtoneAndIncrementVersion(resolved.first, resolved.second)
                    notificationSoundUpdater.recreateRingingCallChannel(resolved.first, newVersion)
                    if (resolved.first !is NotificationSound.Custom) {
                        notificationSoundCopier.deleteStoredSoundFor(SoundSlot.Call)
                    }
                }.onFailure(onChannelFailure)
            }
        }
    }

    /**
     * Custom rows prefer [persistedTitle] (captured at copy time), fall back to a live resolver
     * probe for legacy data, then to a localised "Custom" — never to [defaultLabel], so an
     * unresolvable Custom isn't mislabelled as Default.
     */
    @Composable
    fun probeSoundDisplayName(
        sound: NotificationSound,
        persistedTitle: String?,
        defaultLabel: String,
    ): String = when (sound) {
        NotificationSound.SystemDefault -> defaultLabel
        NotificationSound.ElementDefault -> stringProvider.getString(R.string.screen_notification_settings_sound_element_default)
        NotificationSound.ElementFade -> stringProvider.getString(R.string.screen_notification_settings_sound_element_fade)
        NotificationSound.Silent -> stringProvider.getString(R.string.screen_notification_settings_sound_silent)
        is NotificationSound.Custom -> {
            val nonBlankPersisted = persistedTitle?.takeUnless { it.isBlank() }
            if (nonBlankPersisted != null) {
                nonBlankPersisted
            } else {
                val customFallback = stringProvider.getString(R.string.screen_notification_settings_sound_custom_fallback)
                val resolved by produceState(initialValue = "", sound.uri) {
                    val title = soundDisplayNameResolver.resolveCustomSoundTitle(sound.uri)
                    value = title?.takeUnless { it.isBlank() } ?: customFallback
                }
                resolved
            }
        }
    }

    /**
     * For a Custom pick, copies into app-private storage and returns (FileProvider URI, title).
     * Returns null on copy failure after invoking [onCopyError]. Pass-through for SystemDefault /
     * Silent.
     */
    private suspend fun resolvePickedSound(
        requested: NotificationSound,
        slot: SoundSlot,
        onCopyError: () -> Unit,
    ): Pair<NotificationSound, String?>? {
        if (requested !is NotificationSound.Custom) return requested to null
        return when (val result = notificationSoundCopier.copyToAppFiles(requested.uri, slot)) {
            is CopyResult.Success -> NotificationSound.Custom(result.fileProviderUriString) to result.displayName
            is CopyResult.Failure -> {
                Timber.w(result.cause, "Notification sound copy failed for slot=%s", slot)
                onCopyError()
                null
            }
            CopyResult.UnplayableSource,
            CopyResult.UnplayableCopy,
            CopyResult.FileTooLarge -> {
                Timber.w("Notification sound rejected: result=%s slot=%s", result::class.simpleName, slot)
                onCopyError()
                null
            }
        }
    }
}
