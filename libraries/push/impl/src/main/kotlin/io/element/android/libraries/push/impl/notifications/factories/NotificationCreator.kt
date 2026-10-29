/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.notifications.factories

import android.app.Notification
import android.graphics.Bitmap
import androidx.annotation.ColorInt
import coil3.ImageLoader
import io.element.android.libraries.matrix.api.core.MatrixPatterns
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.push.impl.notifications.RoomEventGroupInfo
import io.element.android.libraries.push.impl.notifications.model.FallbackNotifiableEvent
import io.element.android.libraries.push.impl.notifications.model.InviteNotifiableEvent
import io.element.android.libraries.push.impl.notifications.model.NotifiableMessageEvent
import io.element.android.libraries.push.impl.notifications.model.SimpleNotifiableEvent

interface NotificationCreator {
    suspend fun prepare()

    /**
     * Create a notification for a Room.
     */
    suspend fun createMessagesListNotification(
        notificationAccountParams: NotificationAccountParams,
        roomInfo: RoomEventGroupInfo,
        threadId: ThreadId?,
        largeIcon: Bitmap?,
        lastMessageTimestamp: Long,
        tickerText: String,
        existingNotification: Notification?,
        imageLoader: ImageLoader,
        events: List<NotifiableMessageEvent>,
    ): Notification

    fun createRoomInvitationNotification(
        notificationAccountParams: NotificationAccountParams,
        inviteNotifiableEvent: InviteNotifiableEvent,
    ): Notification

    fun createSimpleEventNotification(
        notificationAccountParams: NotificationAccountParams,
        simpleNotifiableEvent: SimpleNotifiableEvent,
    ): Notification

    fun createFallbackNotification(
        existingNotification: Notification?,
        notificationAccountParams: NotificationAccountParams,
        fallbackNotifiableEvents: List<FallbackNotifiableEvent>,
    ): Notification

    /**
     * Create the summary notification.
     */
    fun createSummaryListNotification(
        notificationAccountParams: NotificationAccountParams,
        compatSummary: String,
        noisy: Boolean,
        lastMessageTimestamp: Long,
    ): Notification

    fun createDiagnosticNotification(
        @ColorInt color: Int,
    ): Notification

    fun createUnregistrationNotification(
        notificationAccountParams: NotificationAccountParams,
    ): Notification

    companion object {
        /**
         * Creates a tag for a message notification given its [roomId] and optional [threadId].
         */
        fun messageTag(roomId: RoomId, threadId: ThreadId?): String = buildString {
            append(roomId)
            threadId?.let {
                append(SEPARATOR)
                append(it)
            }
        }

        /**
         * Extracts the [RoomId] from a message notification [tag] created by [messageTag].
         * Returns null if the tag does not start with a valid room id.
         */
        fun getMessageNotificationRoomId(tag: String): RoomId? {
            return tag.substringBefore(SEPARATOR)
                .takeIf(MatrixPatterns::isRoomId)
                ?.let(::RoomId)
        }

        private const val SEPARATOR = "|"
    }
}
