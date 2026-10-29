/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.notifications.factories

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.annotation.ColorInt
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.MessagingStyle
import coil3.ImageLoader
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.designsystem.utils.CommonDrawables
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.timeline.item.event.EventType
import io.element.android.libraries.push.api.notifications.NotificationBitmapLoader
import io.element.android.libraries.push.impl.R
import io.element.android.libraries.push.impl.notifications.RoomEventGroupInfo
import io.element.android.libraries.push.impl.notifications.channels.NotificationChannels
import io.element.android.libraries.push.impl.notifications.debug.annotateForDebug
import io.element.android.libraries.push.impl.notifications.factories.action.AcceptInvitationActionFactory
import io.element.android.libraries.push.impl.notifications.factories.action.MarkAsReadActionFactory
import io.element.android.libraries.push.impl.notifications.factories.action.QuickReplyActionFactory
import io.element.android.libraries.push.impl.notifications.factories.action.RejectInvitationActionFactory
import io.element.android.libraries.push.impl.notifications.model.FallbackNotifiableEvent
import io.element.android.libraries.push.impl.notifications.model.InviteNotifiableEvent
import io.element.android.libraries.push.impl.notifications.model.NotifiableMessageEvent
import io.element.android.libraries.push.impl.notifications.model.SimpleNotifiableEvent
import io.element.android.libraries.push.impl.notifications.shortcut.createShortcutId
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.services.appnavstate.api.ROOM_OPENED_FROM_NOTIFICATION
import io.element.android.services.toolbox.api.strings.StringProvider

@ContributesBinding(AppScope::class)
class DefaultNotificationCreator(
    @ApplicationContext private val context: Context,
    private val notificationChannels: NotificationChannels,
    private val stringProvider: StringProvider,
    private val buildMeta: BuildMeta,
    private val pendingIntentFactory: PendingIntentFactory,
    private val markAsReadActionFactory: MarkAsReadActionFactory,
    private val quickReplyActionFactory: QuickReplyActionFactory,
    private val bitmapLoader: NotificationBitmapLoader,
    private val acceptInvitationActionFactory: AcceptInvitationActionFactory,
    private val rejectInvitationActionFactory: RejectInvitationActionFactory,
) : NotificationCreator {
    override suspend fun prepare() = notificationChannels.initialize()

    /**
     * Create a notification for a Room.
     */
    override suspend fun createMessagesListNotification(
        notificationAccountParams: NotificationAccountParams,
        roomInfo: RoomEventGroupInfo,
        threadId: ThreadId?,
        largeIcon: Bitmap?,
        lastMessageTimestamp: Long,
        tickerText: String,
        existingNotification: Notification?,
        imageLoader: ImageLoader,
        events: List<NotifiableMessageEvent>,
    ): Notification {
        prepare()
        // Build the pending intent for when the notification is clicked
        val eventId = events.firstOrNull()?.eventId
        val openIntent = when {
            threadId != null -> pendingIntentFactory.createOpenThreadPendingIntent(roomInfo.sessionId, roomInfo.roomId, eventId, threadId)
            else -> pendingIntentFactory.createOpenRoomPendingIntent(
                sessionId = roomInfo.sessionId,
                roomId = roomInfo.roomId,
                eventId = eventId,
                extras = Bundle().apply { putBoolean(ROOM_OPENED_FROM_NOTIFICATION, true) },
            )
        }
        val containsMissedCall = events.any { it.type == EventType.RTC_NOTIFICATION }
        val channelId = if (containsMissedCall) {
            notificationChannels.getChannelForIncomingCall(false)
        } else {
            notificationChannels.getChannelIdForMessage(
                sessionId = roomInfo.sessionId,
                noisy = roomInfo.shouldBing,
            )
        }
        // A category allows groups of notifications to be ranked and filtered – per user or system settings.
        // For example, alarm notifications should display before promo notifications, or message from known contact
        // that can be displayed in not disturb mode if white listed (the later will need compat28.x)
        // If any of the events are of rtc notification type it means a missed call, set the category to the right value
        val category = if (containsMissedCall) {
            NotificationCompat.CATEGORY_MISSED_CALL
        } else {
            NotificationCompat.CATEGORY_MESSAGE
        }
        val builder = if (existingNotification != null) {
            NotificationCompat.Builder(context, existingNotification)
                // Clear existing actions
                .clearActions()
        } else {
            NotificationCompat.Builder(context, channelId)
                // ID of the corresponding shortcut, for conversation features under API 30+
                // Must match those created in the ShortcutInfoCompat.Builder()
                // for the notification to appear as a "Conversation":
                // https://developer.android.com/develop/ui/views/notifications/conversations
                .apply {
                    if (threadId == null) {
                        setShortcutId(createShortcutId(roomInfo.sessionId, roomInfo.roomId))
                    }
                }
                .setGroupSummary(false)
                // In order to avoid notification making sound twice (due to the summary notification)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                // Remove notification after opening it or using an action
                .setAutoCancel(true)
        }
        val messagingStyle = existingNotification?.let {
            MessagingStyle.extractMessagingStyleFromNotification(it)
        } ?: createMessagingStyleFromCurrentUser(
            stringProvider = stringProvider,
            bitmapLoader = bitmapLoader,
            user = notificationAccountParams.user,
            imageLoader = imageLoader,
            roomName = roomInfo.roomDisplayName,
            isThread = threadId != null,
            roomIsGroup = !roomInfo.isDm,
        )
        val newEvents = messagingStyle.filterOutAlreadyDisplayedEvents(events)
        messagingStyle.addMessagesFromEvents(newEvents, imageLoader, stringProvider, bitmapLoader)
        return builder
            .setCategory(category)
            .setNumber(events.size)
            .setOnlyAlertOnce(roomInfo.isUpdated || newEvents.isEmpty())
            .setWhen(lastMessageTimestamp)
            // MESSAGING_STYLE sets title and content for API 16 and above devices.
            .setStyle(messagingStyle)
            .configureWith(notificationAccountParams)
            // Mark room/thread as read
            .addAction(markAsReadActionFactory.create(roomInfo, threadId))
            .setContentIntent(openIntent)
            .setLargeIcon(largeIcon)
            .setDeleteIntent(pendingIntentFactory.createDismissRoomPendingIntent(roomInfo.sessionId, roomInfo.roomId))
            .apply {
                // Sets priority for 25 and below. For 26 and above, 'priority' is deprecated for
                // 'importance' which is set in the NotificationChannel. The integers representing
                // 'priority' are different from 'importance', so make sure you don't mix them.
                if (roomInfo.shouldBing) {
                    priority = NotificationCompat.PRIORITY_DEFAULT
                    setLights(notificationAccountParams.color, 500, 500)
                } else {
                    priority = NotificationCompat.PRIORITY_LOW
                }
                // Quick reply
                if (!roomInfo.hasSmartReplyError) {
                    val latestEventId = events.lastOrNull()?.eventId
                    addAction(quickReplyActionFactory.create(roomInfo, latestEventId, threadId))
                }
            }
            .setTicker(tickerText)
            .build()
    }

    override fun createRoomInvitationNotification(
        notificationAccountParams: NotificationAccountParams,
        inviteNotifiableEvent: InviteNotifiableEvent,
    ): Notification {
        val channelId = notificationChannels.getChannelIdForMessage(
            sessionId = inviteNotifiableEvent.sessionId,
            noisy = inviteNotifiableEvent.noisy,
        )
        return NotificationCompat.Builder(context, channelId)
            .setOnlyAlertOnce(true)
            .setContentTitle((inviteNotifiableEvent.roomName ?: buildMeta.applicationName).annotateForDebug(5))
            .setContentText(inviteNotifiableEvent.description.annotateForDebug(6))
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
            .configureWith(notificationAccountParams)
            .addAction(rejectInvitationActionFactory.create(inviteNotifiableEvent))
            .addAction(acceptInvitationActionFactory.create(inviteNotifiableEvent))
            // Build the pending intent for when the notification is clicked
            .setContentIntent(
                pendingIntentFactory.createOpenRoomPendingIntent(
                    sessionId = inviteNotifiableEvent.sessionId,
                    roomId = inviteNotifiableEvent.roomId,
                    eventId = null,
                )
            )
            .apply {
                if (inviteNotifiableEvent.noisy) {
                    // Compat
                    priority = NotificationCompat.PRIORITY_DEFAULT
                    setLights(notificationAccountParams.color, 500, 500)
                } else {
                    priority = NotificationCompat.PRIORITY_LOW
                }
            }
            .setDeleteIntent(
                pendingIntentFactory.createDismissInvitePendingIntent(
                    inviteNotifiableEvent.sessionId,
                    inviteNotifiableEvent.roomId,
                )
            )
            .setAutoCancel(true)
            .build()
    }

    override fun createSimpleEventNotification(
        notificationAccountParams: NotificationAccountParams,
        simpleNotifiableEvent: SimpleNotifiableEvent,
    ): Notification {
        val channelId = notificationChannels.getChannelIdForMessage(
            sessionId = simpleNotifiableEvent.sessionId,
            noisy = simpleNotifiableEvent.noisy,
        )
        return NotificationCompat.Builder(context, channelId)
            .setOnlyAlertOnce(true)
            .setContentTitle(buildMeta.applicationName.annotateForDebug(7))
            .setContentText(simpleNotifiableEvent.description.annotateForDebug(8))
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
            .configureWith(notificationAccountParams)
            .setAutoCancel(true)
            .setContentIntent(
                pendingIntentFactory.createOpenRoomPendingIntent(
                    sessionId = simpleNotifiableEvent.sessionId,
                    roomId = simpleNotifiableEvent.roomId,
                    eventId = null,
                    extras = Bundle().apply { putBoolean(ROOM_OPENED_FROM_NOTIFICATION, true) },
                )
            )
            .apply {
                if (simpleNotifiableEvent.noisy) {
                    // Compat
                    priority = NotificationCompat.PRIORITY_DEFAULT
                    setLights(notificationAccountParams.color, 500, 500)
                } else {
                    priority = NotificationCompat.PRIORITY_LOW
                }
            }
            .build()
    }

    override fun createFallbackNotification(
        existingNotification: Notification?,
        notificationAccountParams: NotificationAccountParams,
        fallbackNotifiableEvents: List<FallbackNotifiableEvent>,
    ): Notification {
        val fallbackNotifiableEvent = fallbackNotifiableEvents.first()
        val channelId = notificationChannels.getChannelIdForMessage(
            sessionId = fallbackNotifiableEvent.sessionId,
            noisy = fallbackNotifiableEvents.any { it.noisy },
        )
        val existingCounter = existingNotification
            ?.extras
            ?.getInt(FALLBACK_COUNTER_EXTRA)
            ?: 0
        val counter = existingCounter + fallbackNotifiableEvents.size
        return NotificationCompat.Builder(context, channelId)
            .setOnlyAlertOnce(true)
            .setContentTitle(buildMeta.applicationName.annotateForDebug(7))
            .setContentText(
                stringProvider.getQuantityString(R.plurals.notification_fallback_n_content, counter, counter)
                    .annotateForDebug(8)
            )
            .setExtras(
                Bundle().apply { putInt(FALLBACK_COUNTER_EXTRA, counter) },
            )
            .setNumber(counter)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
            .configureWith(notificationAccountParams)
            .setAutoCancel(true)
            .setWhen(fallbackNotifiableEvent.timestamp)
            .setContentIntent(pendingIntentFactory.createOpenSessionPendingIntent(fallbackNotifiableEvent.sessionId))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Create the summary notification.
     */
    override fun createSummaryListNotification(
        notificationAccountParams: NotificationAccountParams,
        compatSummary: String,
        noisy: Boolean,
        lastMessageTimestamp: Long,
    ): Notification {
        val userId = notificationAccountParams.user.userId
        val channelId = notificationChannels.getChannelIdForMessage(
            sessionId = userId,
            noisy = noisy,
        )
        return NotificationCompat.Builder(context, channelId)
            .setOnlyAlertOnce(true)
            // used in compat < N, after summary is built based on child notifications
            .setWhen(lastMessageTimestamp)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            // set this notification as the summary for the group
            .setGroupSummary(true)
            .configureWith(notificationAccountParams)
            .apply {
                if (noisy) {
                    // Compat
                    priority = NotificationCompat.PRIORITY_DEFAULT
                    setLights(notificationAccountParams.color, 500, 500)
                } else {
                    // compat
                    priority = NotificationCompat.PRIORITY_LOW
                }
            }
            .setContentIntent(pendingIntentFactory.createOpenSessionPendingIntent(userId))
            .setDeleteIntent(pendingIntentFactory.createDismissSummaryPendingIntent(userId))
            .build()
    }

    override fun createDiagnosticNotification(
        @ColorInt color: Int,
    ): Notification {
        val intent = pendingIntentFactory.createTestPendingIntent()
        return NotificationCompat.Builder(context, notificationChannels.getChannelIdForTest())
            .setContentTitle(buildMeta.applicationName)
            .setContentText(stringProvider.getString(R.string.notification_test_push_notification_content))
            .setSmallIcon(CommonDrawables.ic_notification)
            .setColor(color)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .setDeleteIntent(intent)
            .build()
    }

    override fun createUnregistrationNotification(
        notificationAccountParams: NotificationAccountParams,
    ): Notification {
        val userId = notificationAccountParams.user.userId
        val text = stringProvider.getString(R.string.notification_error_unified_push_unregistered_android)
        return NotificationCompat.Builder(context, notificationChannels.getChannelIdForTest())
            .setSubText(userId.value)
            // The text is long and can be truncated so use BigTextStyle.
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentTitle(stringProvider.getString(CommonStrings.dialog_title_warning))
            .setContentText(text)
            .configureWith(notificationAccountParams)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(pendingIntentFactory.createOpenSessionPendingIntent(userId))
            .build()
    }

    companion object {
        const val MESSAGE_EVENT_ID = "message_event_id"
        private const val FALLBACK_COUNTER_EXTRA = "COUNTER"
    }
}

private fun NotificationCompat.Builder.configureWith(notificationAccountParams: NotificationAccountParams) = apply {
    setSmallIcon(CommonDrawables.ic_notification)
    setColor(notificationAccountParams.color)
    setGroup(notificationAccountParams.user.userId.value)
    if (notificationAccountParams.showSessionId) {
        setSubText(notificationAccountParams.user.userId.value)
    }
}
