/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.element.android.libraries.architecture.coverage.ExcludeFromCoverage
import io.element.android.libraries.designsystem.preview.ElementPreviewDark
import io.element.android.libraries.designsystem.preview.ElementPreviewLight
import io.element.android.libraries.designsystem.preview.PreviewWithLargeHeight
import io.element.android.libraries.designsystem.utils.CommonDrawables
import io.element.android.libraries.emoji.api.picker.NoOpEmojiPickerRenderer

@PreviewWithLargeHeight
@Composable
internal fun PreferencesRootViewLightPreview(@PreviewParameter(PreferencesRootStatePreviewParam::class) state: PreferencesRootState) =
    ElementPreviewLight(
        drawableFallbackForImages = CommonDrawables.sample_avatar,
    ) { ContentToPreview(state) }

@PreviewWithLargeHeight
@Composable
internal fun PreferencesRootViewDarkPreview(@PreviewParameter(PreferencesRootStatePreviewParam::class) state: PreferencesRootState) =
    ElementPreviewDark(
        drawableFallbackForImages = CommonDrawables.sample_avatar,
    ) { ContentToPreview(state) }

@ExcludeFromCoverage
@Composable
private fun ContentToPreview(state: PreferencesRootState) {
    PreferencesRootView(
        state = state,
        emojiPickerRenderer = NoOpEmojiPickerRenderer,
        onBackClick = {},
        onAddAccountClick = {},
        onSwitchToSession = {},
        onOpenAnalytics = {},
        onOpenDeveloperSettings = {},
        onOpenLocationSettings = {},
        onOpenMediaSettings = {},
        onOpenLabs = {},
        onOpenAbout = {},
        onOpenLockScreenSettings = {},
        onEditProfileClick = {},
        onSecureBackupClick = {},
        onManageAccountClick = {},
        onLinkNewDeviceClick = {},
        onOpenRageShake = {},
        onModerationAndSafetyClick = {},
        onOpenNotificationSettings = {},
        onSignOutClick = {},
        onDeactivateClick = {},
    )
}
