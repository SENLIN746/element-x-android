/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runUpdatingStateNoSuccess
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.ShowAllActivityInRoomListFeature
import io.element.android.libraries.fullscreenintent.api.FullScreenIntentPermissionsState
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.notificationsettings.NotificationSettingsService
import io.element.android.libraries.matrix.api.room.RoomNotificationMode
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.NotificationSound
import io.element.android.libraries.push.api.PushService
import io.element.android.libraries.push.api.notifications.NotificationSoundUpdater
import io.element.android.libraries.push.api.notifications.SoundDisplayNameResolver
import io.element.android.libraries.push.api.notifications.sound.NotificationSoundCopier
import io.element.android.libraries.pushstore.api.UserPushStore
import io.element.android.libraries.pushstore.api.UserPushStoreFactory
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@Inject
class NotificationSettingsPresenter(
    private val notificationSettingsService: NotificationSettingsService,
    private val userPushStoreFactory: UserPushStoreFactory,
    private val matrixClient: MatrixClient,
    private val pushService: PushService,
    private val systemNotificationsEnabledProvider: SystemNotificationsEnabledProvider,
    private val fullScreenIntentPermissionsPresenter: Presenter<FullScreenIntentPermissionsState>,
    private val appPreferencesStore: AppPreferencesStore,
    private val notificationSoundUpdater: NotificationSoundUpdater,
    private val notificationSoundCopier: NotificationSoundCopier,
    private val soundDisplayNameResolver: SoundDisplayNameResolver,
    private val stringProvider: StringProvider,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
    private val featureFlagService: FeatureFlagService,
    private val dispatchers: CoroutineDispatchers,
) : Presenter<NotificationSettingsState> {
    private val soundActions = NotificationSoundSettingsActions(
        appPreferencesStore,
        notificationSoundUpdater,
        notificationSoundCopier,
        soundDisplayNameResolver,
        stringProvider,
    )

    @Composable
    override fun present(): NotificationSettingsState {
        val userPushStore = remember { userPushStoreFactory.getOrCreate(matrixClient.sessionId) }
        val systemNotificationsEnabled: MutableState<Boolean> = remember {
            mutableStateOf(systemNotificationsEnabledProvider.notificationsEnabled())
        }
        val changeNotificationSettingAction: MutableState<AsyncAction<Unit>> = remember { mutableStateOf(AsyncAction.Uninitialized) }

        val localCoroutineScope = rememberCoroutineScope()
        val appNotificationsEnabled by remember {
            userPushStore.getNotificationEnabledForDevice()
        }.collectAsState(initial = false)

        val matrixSettings: MutableState<NotificationSettingsState.MatrixSettings> = remember {
            mutableStateOf(NotificationSettingsState.MatrixSettings.Uninitialized)
        }

        // Used to force a recomposition
        var refreshFullScreenIntentSettings by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            fetchSettings(matrixSettings)
            observeNotificationSettings(matrixSettings, changeNotificationSettingAction)
        }

        var refreshPushProvider by remember { mutableIntStateOf(0) }
        val pushSettings = rememberNotificationPushSettings(
            matrixClient,
            pushService,
            dispatchers,
            appNotificationsEnabled,
            refreshPushProvider,
        )

        val messageSound by remember { appPreferencesStore.getMessageSoundFlow() }.collectAsState(initial = NotificationSound.SystemDefault)
        val callRingtone by remember { appPreferencesStore.getCallRingtoneFlow() }.collectAsState(initial = NotificationSound.SystemDefault)
        val persistedMessageSoundTitle by remember { appPreferencesStore.getMessageSoundDisplayNameFlow() }.collectAsState(initial = null)
        val persistedCallRingtoneTitle by remember { appPreferencesStore.getCallRingtoneDisplayNameFlow() }.collectAsState(initial = null)
        val defaultLabel = stringProvider.getString(R.string.screen_notification_settings_sound_system_default)

        // One-shot classification for the call-ringtone slot only: users who customised the
        // channel via Android system settings before the in-app picker existed (channel version 0,
        // persisted SystemDefault) get reflected here. The message-sound equivalent is handled by
        // the eager migration in DefaultNotificationChannels.createNotificationChannels(). Cleared
        // on any in-app pick so a stale read can't bleed into the row after the recreate.
        var legacyCallRingtone by remember { mutableStateOf<NotificationSound?>(null) }
        LaunchedEffect(Unit) {
            val config = appPreferencesStore.getNotificationSoundChannelConfig()
            if (config.callRingtone == NotificationSound.SystemDefault && config.callRingtoneVersion == 0) {
                legacyCallRingtone = notificationSoundUpdater.readRingingCallChannelSound()
            }
        }

        val effectiveMessageSound = messageSound
        val effectiveCallRingtone = if (callRingtone == NotificationSound.SystemDefault) {
            legacyCallRingtone ?: callRingtone
        } else {
            callRingtone
        }

        val messageSoundDisplayName = soundActions.probeSoundDisplayName(effectiveMessageSound, persistedMessageSoundTitle, defaultLabel)
        val callRingtoneDisplayName = soundActions.probeSoundDisplayName(effectiveCallRingtone, persistedCallRingtoneTitle, defaultLabel)

        var messageSoundCopyError by remember { mutableStateOf(false) }
        var callRingtoneCopyError by remember { mutableStateOf(false) }
        var showMessageSoundDialog by remember { mutableStateOf(false) }
        var pendingMessageSoundPickerLaunch by remember { mutableIntStateOf(0) }
        var showCallRingtoneDialog by remember { mutableStateOf(false) }
        var pendingCallRingtonePickerLaunch by remember { mutableIntStateOf(0) }

        val showAllActivityInRoomList by produceState(false) {
            featureFlagService.isFeatureEnabledFlow(ShowAllActivityInRoomListFeature)
                .collect { value = it }
        }

        fun handleEvent(event: NotificationSettingsEvent) {
            when (event) {
                NotificationSettingsEvent.RetryPushRegistration -> pushSettings.retry()
                is NotificationSettingsEvent.SetAtRoomNotificationsEnabled -> {
                    localCoroutineScope.setAtRoomNotificationsEnabled(event.enabled, changeNotificationSettingAction)
                }
                is NotificationSettingsEvent.SetCallNotificationsEnabled -> {
                    localCoroutineScope.setCallNotificationsEnabled(event.enabled, changeNotificationSettingAction)
                }
                is NotificationSettingsEvent.SetInviteForMeNotificationsEnabled -> {
                    localCoroutineScope.setInviteForMeNotificationsEnabled(event.enabled, changeNotificationSettingAction)
                }
                is NotificationSettingsEvent.SetNotificationsEnabled -> sessionCoroutineScope.setNotificationsEnabled(userPushStore, event.enabled)
                NotificationSettingsEvent.ClearConfigurationMismatchError -> {
                    matrixSettings.value = NotificationSettingsState.MatrixSettings.Invalid(fixFailed = false)
                }
                NotificationSettingsEvent.FixConfigurationMismatch -> localCoroutineScope.fixConfigurationMismatch(matrixSettings)
                NotificationSettingsEvent.RefreshSystemNotificationsEnabled -> {
                    systemNotificationsEnabled.value = systemNotificationsEnabledProvider.notificationsEnabled()
                    refreshPushProvider++
                    refreshFullScreenIntentSettings++
                }
                NotificationSettingsEvent.ClearNotificationChangeError -> changeNotificationSettingAction.value = AsyncAction.Uninitialized
                NotificationSettingsEvent.ChangePushProvider -> pushSettings.openPicker()
                NotificationSettingsEvent.CancelChangePushProvider -> pushSettings.closePicker()
                is NotificationSettingsEvent.SetPushProvider -> pushSettings.select(event.index)
                is NotificationSettingsEvent.SetMessageSound -> soundActions.applyMessageSound(
                    sound = event.sound,
                    sessionCoroutineScope = sessionCoroutineScope,
                    onCopyError = { messageSoundCopyError = true },
                    onCopySuccess = { messageSoundCopyError = false },
                    onChannelFailure = { failure -> changeNotificationSettingAction.value = AsyncAction.Failure(failure) },
                )
                is NotificationSettingsEvent.SelectMessageSoundPreset -> {
                    showMessageSoundDialog = false
                    soundActions.applyMessageSound(
                        sound = event.sound,
                        sessionCoroutineScope = sessionCoroutineScope,
                        onCopyError = { messageSoundCopyError = true },
                        onCopySuccess = { messageSoundCopyError = false },
                        onChannelFailure = { failure -> changeNotificationSettingAction.value = AsyncAction.Failure(failure) },
                    )
                }
                NotificationSettingsEvent.ShowMessageSoundDialog -> showMessageSoundDialog = true
                NotificationSettingsEvent.DismissMessageSoundDialog -> showMessageSoundDialog = false
                NotificationSettingsEvent.LaunchMessageSoundPicker -> {
                    showMessageSoundDialog = false
                    pendingMessageSoundPickerLaunch++
                }
                is NotificationSettingsEvent.SetCallRingtone -> {
                    legacyCallRingtone = null
                    soundActions.applyCallRingtone(
                        sound = event.sound,
                        sessionCoroutineScope = sessionCoroutineScope,
                        onCopyError = { callRingtoneCopyError = true },
                        onCopySuccess = { callRingtoneCopyError = false },
                        onChannelFailure = { failure -> changeNotificationSettingAction.value = AsyncAction.Failure(failure) },
                    )
                }
                is NotificationSettingsEvent.SelectCallRingtonePreset -> {
                    showCallRingtoneDialog = false
                    legacyCallRingtone = null
                    soundActions.applyCallRingtone(
                        sound = event.sound,
                        sessionCoroutineScope = sessionCoroutineScope,
                        onCopyError = { callRingtoneCopyError = true },
                        onCopySuccess = { callRingtoneCopyError = false },
                        onChannelFailure = { failure -> changeNotificationSettingAction.value = AsyncAction.Failure(failure) },
                    )
                }
                NotificationSettingsEvent.ShowCallRingtoneDialog -> showCallRingtoneDialog = true
                NotificationSettingsEvent.DismissCallRingtoneDialog -> showCallRingtoneDialog = false
                NotificationSettingsEvent.LaunchCallRingtonePicker -> {
                    showCallRingtoneDialog = false
                    pendingCallRingtonePickerLaunch++
                }
                NotificationSettingsEvent.DismissMessageSoundCopyError -> {
                    messageSoundCopyError = false
                }
                NotificationSettingsEvent.DismissCallRingtoneCopyError -> {
                    callRingtoneCopyError = false
                }
                is NotificationSettingsEvent.ToggleShowAllActivityInRoomList -> localCoroutineScope.launch {
                    featureFlagService.setFeatureEnabled(ShowAllActivityInRoomListFeature, event.newValue)
                }
            }
        }

        return NotificationSettingsState(
            matrixSettings = matrixSettings.value,
            appSettings = NotificationSettingsState.AppSettings(
                systemNotificationsEnabled = systemNotificationsEnabled.value,
                appNotificationsEnabled = appNotificationsEnabled,
            ),
            changeNotificationSettingAction = changeNotificationSettingAction.value,
            currentPushDistributor = pushSettings.current,
            pushRegistrationState = pushSettings.registration,
            availablePushDistributors = pushSettings.available,
            showChangePushProviderDialog = pushSettings.showPicker,
            fullScreenIntentPermissionsState = key(refreshFullScreenIntentSettings) { fullScreenIntentPermissionsPresenter.present() },
            messageSound = NotificationSettingsState.SoundChannelUiState(
                sound = effectiveMessageSound,
                displayName = messageSoundDisplayName,
                copyError = messageSoundCopyError,
            ),
            callRingtone = NotificationSettingsState.SoundChannelUiState(
                sound = effectiveCallRingtone,
                displayName = callRingtoneDisplayName,
                copyError = callRingtoneCopyError,
            ),
            showMessageSoundDialog = showMessageSoundDialog,
            showCallRingtoneDialog = showCallRingtoneDialog,
            pendingMessageSoundPickerLaunch = pendingMessageSoundPickerLaunch,
            pendingCallRingtonePickerLaunch = pendingCallRingtonePickerLaunch,
            showAllActivityInRoomList = showAllActivityInRoomList,
            eventSink = ::handleEvent,
        )
    }

    @OptIn(FlowPreview::class)
    private fun CoroutineScope.observeNotificationSettings(
        target: MutableState<NotificationSettingsState.MatrixSettings>,
        changeNotificationSettingAction: MutableState<AsyncAction<Unit>>,
    ) {
        notificationSettingsService.notificationSettingsChangeFlow
            .debounce(0.5.seconds)
            .onEach {
                fetchSettings(target)
                changeNotificationSettingAction.value = AsyncAction.Uninitialized
            }
            .launchIn(this)
    }

    private fun CoroutineScope.fetchSettings(target: MutableState<NotificationSettingsState.MatrixSettings>) = launch {
        val groupDefaultMode = notificationSettingsService.getDefaultRoomNotificationMode(isEncrypted = false, isOneToOne = false).getOrThrow()
        val encryptedGroupDefaultMode = notificationSettingsService.getDefaultRoomNotificationMode(isEncrypted = true, isOneToOne = false).getOrThrow()

        val oneToOneDefaultMode = notificationSettingsService.getDefaultRoomNotificationMode(isEncrypted = false, isOneToOne = true).getOrThrow()
        val encryptedOneToOneDefaultMode = notificationSettingsService.getDefaultRoomNotificationMode(isEncrypted = true, isOneToOne = true).getOrThrow()

        if (groupDefaultMode != encryptedGroupDefaultMode || oneToOneDefaultMode != encryptedOneToOneDefaultMode) {
            target.value = NotificationSettingsState.MatrixSettings.Invalid(fixFailed = false)
            return@launch
        }

        val callNotificationsEnabled = notificationSettingsService.isCallEnabled().getOrThrow()
        val atRoomNotificationsEnabled = notificationSettingsService.isRoomMentionEnabled().getOrThrow()
        val inviteForMeNotificationsEnabled = notificationSettingsService.isInviteForMeEnabled().getOrThrow()

        target.value = NotificationSettingsState.MatrixSettings.Valid(
            atRoomNotificationsEnabled = atRoomNotificationsEnabled,
            callNotificationsEnabled = callNotificationsEnabled,
            inviteForMeNotificationsEnabled = inviteForMeNotificationsEnabled,
            defaultGroupNotificationMode = encryptedGroupDefaultMode,
            defaultOneToOneNotificationMode = encryptedOneToOneDefaultMode,
        )
    }

    private fun CoroutineScope.fixConfigurationMismatch(target: MutableState<NotificationSettingsState.MatrixSettings>) = launch {
        runCatchingExceptions {
            val groupDefaultMode = notificationSettingsService.getDefaultRoomNotificationMode(isEncrypted = false, isOneToOne = false).getOrThrow()
            val encryptedGroupDefaultMode = notificationSettingsService.getDefaultRoomNotificationMode(isEncrypted = true, isOneToOne = false).getOrThrow()

            if (groupDefaultMode != encryptedGroupDefaultMode) {
                notificationSettingsService.setDefaultRoomNotificationMode(
                    isEncrypted = encryptedGroupDefaultMode != RoomNotificationMode.ALL_MESSAGES,
                    mode = RoomNotificationMode.ALL_MESSAGES,
                    isDM = false,
                )
            }

            val oneToOneDefaultMode = notificationSettingsService.getDefaultRoomNotificationMode(isEncrypted = false, isOneToOne = true).getOrThrow()
            val encryptedOneToOneDefaultMode = notificationSettingsService.getDefaultRoomNotificationMode(isEncrypted = true, isOneToOne = true).getOrThrow()

            if (oneToOneDefaultMode != encryptedOneToOneDefaultMode) {
                notificationSettingsService.setDefaultRoomNotificationMode(
                    isEncrypted = encryptedOneToOneDefaultMode != RoomNotificationMode.ALL_MESSAGES,
                    mode = RoomNotificationMode.ALL_MESSAGES,
                    isDM = true,
                )
            }
        }.fold(
            onSuccess = {},
            onFailure = {
                target.value = NotificationSettingsState.MatrixSettings.Invalid(fixFailed = true)
            }
        )
    }

    private fun CoroutineScope.setAtRoomNotificationsEnabled(enabled: Boolean, action: MutableState<AsyncAction<Unit>>) = launch {
        action.runUpdatingStateNoSuccess {
            notificationSettingsService.setRoomMentionEnabled(enabled)
        }
    }

    private fun CoroutineScope.setCallNotificationsEnabled(enabled: Boolean, action: MutableState<AsyncAction<Unit>>) = launch {
        action.runUpdatingStateNoSuccess {
            notificationSettingsService.setCallEnabled(enabled)
        }
    }

    private fun CoroutineScope.setInviteForMeNotificationsEnabled(enabled: Boolean, action: MutableState<AsyncAction<Unit>>) = launch {
        action.runUpdatingStateNoSuccess {
            notificationSettingsService.setInviteForMeEnabled(enabled)
        }
    }

    private fun CoroutineScope.setNotificationsEnabled(userPushStore: UserPushStore, enabled: Boolean) = launch {
        userPushStore.setNotificationEnabledForDevice(enabled)
        if (!enabled) {
            pushService.getCurrentPushProvider(matrixClient.sessionId)?.unregister(matrixClient)
        }
    }
}
