package ru.sberdevices.sbdv.notifications

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.telegram.messenger.R
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.services.notification.v2.StarNotificationManager
import ru.sberdevices.services.notification.v2.callback.notificationReasonCallback
import ru.sberdevices.services.notification.v2.entities.NotificationButtonStyle
import ru.sberdevices.services.notification.v2.entities.NotificationImage
import ru.sberdevices.services.notification.v2.entities.StarNotificationBuilder
import ru.sberdevices.services.notification.v2.entities.addButton
import ru.sberdevices.services.notification.v2.entities.reason.NotificationReason
import ru.sberdevices.services.notification.v2.entities.starNotificationBuilder
import java.util.UUID

/**
 * Class for managing StarOS Notifications
 */
private const val CALLBACK_BUTTON_ID = "button_callback"
const val INTENT_EXTRA_STAR_NOTIFICATION_EXTERNAL_UID = "notificationExternalUid"

@Suppress("LogConditional")
class StarNotifications(val starNotificationManager: StarNotificationManager, mainDispatcher: CoroutineDispatcher) {

    private val logger = Logger.get("StarNotifications")

    private val scope = CoroutineScope(mainDispatcher + SupervisorJob())

    fun createMissedCallNotification(
        context: Context,
        fromUserName: String,
        fromUserId: Long,
        message: String
    ) = scope.launch {

        val (notificationExternalUid, preparedDeeplink) =
            generateNotificationUidAndPrepareDeeplink(fromUserId = fromUserId)

        logger.debug {
            "createMissedCallNotification (externalUid = $notificationExternalUid, fromUserName = $fromUserName, " +
                "fromUserId = $fromUserId, message = $message, deeplink = $preparedDeeplink)"
        }

        starNotificationManager.createNotification(
            notificationBuilder = createMissedCallStarNotificationBuilder(
                context = context,
                fromUserName = fromUserName,
                message = message,
                notificationExternalUid = notificationExternalUid,
                preparedDeeplink = preparedDeeplink
            ),
            pendingIntent = scope.notificationReasonCallback(context) { reason ->
                when (reason) {
                    is NotificationReason.ReasonButtonClick,
                    NotificationReason.ReasonSkipped(NotificationReason.ReasonSkipped.SkipType.SKIP_BY_USER) ->
                        scope.launch {
                            logger.debug {
                                "user clicked on recall button or skipped it consciously " +
                                    "-> notification should be removed"
                            }
                            starNotificationManager.removeNotificationByExternalUid(notificationExternalUid)
                        }
                    else -> {
                        logger.debug { "notification added to StarOS Notification Repository" }
                    }
                }
            }
        )
    }

    private fun generateNotificationUidAndPrepareDeeplink(fromUserId: Long): Pair<String, String> {
        val notificationExternalUid = UUID.randomUUID().toString()
        val preparedDeeplink =
            "tg://call?callee_id=$fromUserId&$INTENT_EXTRA_STAR_NOTIFICATION_EXTERNAL_UID=$notificationExternalUid"
        return notificationExternalUid to preparedDeeplink
    }

    private fun createMissedCallStarNotificationBuilder(
        context: Context,
        fromUserName: String,
        message: String,
        notificationExternalUid: String,
        preparedDeeplink: String
    ): StarNotificationBuilder {
        val notificationTitle = "${context.getString(R.string.sbdv_notification_title)} $fromUserName"
        val notificationIcon = NotificationImage.resourceImage("sbdv_ic_videocalls")
        return starNotificationBuilder {
            externalUid = notificationExternalUid
            title = notificationTitle
            text = message
            icon = notificationIcon
            deeplink = preparedDeeplink
            addButton {
                buttonId = CALLBACK_BUTTON_ID
                text = context.getString(R.string.sbdv_notification_callback)
                style = NotificationButtonStyle.PRIMARY_BUTTON
                deeplink = preparedDeeplink
            }
        }
    }

    fun deleteNotificationByExternalUid(externalUid: String) = scope.launch {
        logger.debug { "deleteNotificationByExternalUid(uid = $externalUid)" }
        starNotificationManager.removeNotificationByExternalUid(externalUid)
    }

    fun deleteAllNotificationsOnLogout() = scope.launch {
        logger.debug { "deleteAllNotificationsOnLogout()" }
        starNotificationManager.removeAllNotifications()
    }
}
