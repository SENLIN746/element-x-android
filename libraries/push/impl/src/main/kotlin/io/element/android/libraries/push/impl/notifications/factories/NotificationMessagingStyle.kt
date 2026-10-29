/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.notifications.factories

import androidx.core.app.NotificationCompat.MessagingStyle
import androidx.core.app.Person
import coil3.ImageLoader
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.matrix.ui.model.getBestName
import io.element.android.libraries.push.api.notifications.NotificationBitmapLoader
import io.element.android.libraries.push.impl.R
import io.element.android.libraries.push.impl.notifications.debug.annotateForDebug
import io.element.android.libraries.push.impl.notifications.model.NotifiableMessageEvent
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.services.toolbox.api.strings.StringProvider

internal fun MessagingStyle.filterOutAlreadyDisplayedEvents(
    events: List<NotifiableMessageEvent>,
): List<NotifiableMessageEvent> {
    val displayedEventIds = messages.mapNotNullTo(mutableSetOf()) { it.extras.getString(DefaultNotificationCreator.MESSAGE_EVENT_ID) }
    return events.filter { event ->
        event.isSmartReplyError() || displayedEventIds.add(event.eventId.value)
    }
}

internal suspend fun MessagingStyle.addMessagesFromEvents(
    events: List<NotifiableMessageEvent>,
    imageLoader: ImageLoader,
    stringProvider: StringProvider,
    bitmapLoader: NotificationBitmapLoader,
) {
    events.forEach { event ->
        val senderPerson = if (event.outGoingMessage) {
            null
        } else {
            val senderName = event.senderDisambiguatedDisplayName.orEmpty()
            // If the notification is for a mention or reply, we create a fake `Person` with a custom name and key
            val displayName = if (event.hasMentionOrReply) {
                stringProvider.getString(R.string.notification_sender_mention_reply, senderName)
            } else {
                senderName
            }
            val key = if (event.hasMentionOrReply) {
                "mention-or-reply:${event.eventId.value}"
            } else {
                event.senderId.value
            }
            Person.Builder()
                .setName(displayName.annotateForDebug(70))
                .setIcon(
                    bitmapLoader.getUserIcon(
                        avatarData = AvatarData(
                            id = event.senderId.value,
                            name = senderName,
                            url = event.senderAvatarPath,
                            size = AvatarSize.UserHeader,
                        ),
                        imageLoader = imageLoader,
                    )
                )
                .setKey(key)
                .build()
        }
        when {
            event.isSmartReplyError() -> addMessage(
                stringProvider.getString(R.string.notification_inline_reply_failed),
                event.timestamp,
                senderPerson
            )
            else -> {
                if (event.imageMimeType != null && event.imageUri != null) {
                    // Image case
                    val message = MessagingStyle.Message(
                        // This text will not be rendered, but some systems does not render the image
                        // if the text is null
                        stringProvider.getString(CommonStrings.common_image),
                        event.timestamp,
                        senderPerson,
                    )
                        .setData(event.imageMimeType, event.imageUri)
                    message.extras.putString(DefaultNotificationCreator.MESSAGE_EVENT_ID, event.eventId.value)
                    addMessage(message)
                    // Add additional message for captions
                    if (event.body != null) {
                        addMessage(
                            MessagingStyle.Message(
                                event.body.annotateForDebug(72),
                                event.timestamp,
                                senderPerson,
                            )
                        )
                    }
                } else {
                    // Text case
                    val message = MessagingStyle.Message(
                        event.body?.annotateForDebug(71),
                        event.timestamp,
                        senderPerson
                    )
                    message.extras.putString(DefaultNotificationCreator.MESSAGE_EVENT_ID, event.eventId.value)
                    addMessage(message)
                }
            }
        }
    }
}

internal suspend fun createMessagingStyleFromCurrentUser(
    stringProvider: StringProvider,
    bitmapLoader: NotificationBitmapLoader,
    user: MatrixUser,
    imageLoader: ImageLoader,
    roomName: String,
    isThread: Boolean,
    roomIsGroup: Boolean
): MessagingStyle {
    return MessagingStyle(
        Person.Builder()
            // Note: name cannot be empty else NotificationCompat.MessagingStyle() will crash
            .setName(user.getBestName().annotateForDebug(50))
            .setIcon(
                bitmapLoader.getUserIcon(
                    avatarData = user.getAvatarData(AvatarSize.UserHeader),
                    imageLoader = imageLoader,
                )
            )
            .setKey(user.userId.value)
            .build()
    ).also {
        it.conversationTitle = if (isThread) {
            stringProvider.getString(R.string.notification_thread_in_room, roomName)
        } else {
            roomName
        }
        // So the avatar is displayed even if they're part of a conversation
        it.isGroupConversation = roomIsGroup || isThread
    }
}

fun NotifiableMessageEvent.isSmartReplyError() = outGoingMessage && outGoingMessageFailed
