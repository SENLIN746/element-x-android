/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.notifications

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.components.dialogs.ListOption
import io.element.android.libraries.designsystem.components.dialogs.SingleSelectionDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.preferences.api.store.NotificationSound
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun MessageSoundDialog(state: NotificationSettingsState) {
    val initialSelection = when (state.messageSound.sound) {
        NotificationSound.ElementDefault -> 0
        NotificationSound.ElementFade -> 1
        NotificationSound.SystemDefault -> 2
        else -> null
    }
    // When the user is on Custom or Silent, no preset row is selected — surface the current
    // sound as a subtitle so screen readers (and sighted users) still have state context.
    val subtitle = if (initialSelection == null) {
        stringResource(
            id = R.string.screen_notification_settings_message_sound_dialog_current_subtitle,
            state.messageSound.displayName,
        )
    } else {
        null
    }
    SingleSelectionDialog(
        title = stringResource(id = R.string.screen_notification_settings_message_sound_dialog_title),
        subtitle = subtitle,
        options = persistentListOf(
            ListOption(title = stringResource(id = R.string.screen_notification_settings_sound_element_default)),
            ListOption(title = stringResource(id = R.string.screen_notification_settings_sound_element_fade)),
            ListOption(title = stringResource(id = R.string.screen_notification_settings_sound_system_default)),
            ListOption(title = stringResource(id = R.string.screen_notification_settings_message_sound_dialog_choose_other)),
        ),
        initialSelection = initialSelection,
        onSelectOption = { index ->
            when (index) {
                0 -> state.eventSink(NotificationSettingsEvent.SelectMessageSoundPreset(NotificationSound.ElementDefault))
                1 -> state.eventSink(NotificationSettingsEvent.SelectMessageSoundPreset(NotificationSound.ElementFade))
                2 -> state.eventSink(NotificationSettingsEvent.SelectMessageSoundPreset(NotificationSound.SystemDefault))
                else -> state.eventSink(NotificationSettingsEvent.LaunchMessageSoundPicker)
            }
        },
        onDismissRequest = { state.eventSink(NotificationSettingsEvent.DismissMessageSoundDialog) },
    )
}

@Composable
internal fun CallRingtoneDialog(state: NotificationSettingsState) {
    val initialSelection = when (state.callRingtone.sound) {
        NotificationSound.SystemDefault -> 0
        else -> null
    }
    val subtitle = if (initialSelection == null) {
        stringResource(
            id = R.string.screen_notification_settings_message_sound_dialog_current_subtitle,
            state.callRingtone.displayName,
        )
    } else {
        null
    }
    SingleSelectionDialog(
        title = stringResource(id = R.string.screen_notification_settings_call_ringtone_label),
        subtitle = subtitle,
        options = persistentListOf(
            ListOption(title = stringResource(id = R.string.screen_notification_settings_sound_system_default)),
            ListOption(title = stringResource(id = R.string.screen_notification_settings_message_sound_dialog_choose_other)),
        ),
        initialSelection = initialSelection,
        onSelectOption = { index ->
            when (index) {
                0 -> state.eventSink(NotificationSettingsEvent.SelectCallRingtonePreset(NotificationSound.SystemDefault))
                else -> state.eventSink(NotificationSettingsEvent.LaunchCallRingtonePicker)
            }
        },
        onDismissRequest = { state.eventSink(NotificationSettingsEvent.DismissCallRingtoneDialog) },
    )
}

@Composable
internal fun SoundCopyErrorRow(
    onDismissClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .background(ElementTheme.colors.bgSubtleSecondary)
            .semantics { liveRegion = LiveRegionMode.Polite },
        content = {
            Text(
                text = stringResource(R.string.screen_notification_settings_sound_set_sound_error_title),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
            )
        },
        trailingContent = ListItemContent.Custom { _ ->
            IconButton(onClick = onDismissClick) {
                Icon(
                    imageVector = CompoundIcons.Close(),
                    contentDescription = stringResource(R.string.screen_notification_settings_sound_set_sound_error_dismiss_a11y),
                )
            }
        },
    )
}
