package ru.sberdevices.telegramcalls.vendor.calls.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.telegram.messenger.MessageObject
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.telegramcalls.vendor.calls.data.mapper.toCallDraft
import ru.sberdevices.telegramcalls.vendor.calls.domain.entity.CallsNotification
import ru.sberdevices.telegramcalls.vendor.notifications.data.Notification

/**
 * @author Ирина Карпенко on 21.10.2021
 */
interface CallsNotificationsRepository {
    val notifications: Flow<CallsNotification>
}

internal class CallsNotificationsRepositoryImpl : CallsNotificationsRepository {
    private val logger by Logger.lazy<CallsNotificationsRepositoryImpl>()

    override val notifications: Flow<CallsNotification> = callbackFlow {
        val notificationCenter: NotificationCenter = NotificationCenter.getInstance(UserConfig.selectedAccount)
        val observer = NotificationCenter.NotificationCenterDelegate { id, _, args ->
            logger.debug { "received notification ${Notification.get(id)}" }
            if (id == Notification.didReceiveNewMessages.id || id == Notification.messagesDeleted.id) {
                val scheduled = args.getOrNull(2) as? Boolean == true
                if (!scheduled) {
                    when (id) {
                        Notification.didReceiveNewMessages.id -> {
                            val messages = (args[1] as ArrayList<MessageObject>)
                                .map { it.messageOwner }
                                .filter { it.action is TLRPC.TL_messageActionPhoneCall }
                            val currentAccount = UserConfig.selectedAccount
                            val newCalls = messages.map { it.toCallDraft(currentAccount) }
                            logger.debug { "new calls: $newCalls" }
                            launch { send(CallsNotification.NewCalls(newCalls)) }
                        }
                        Notification.messagesDeleted.id -> launch { send(CallsNotification.Refresh) }
                    }
                }
            }
        }
        notificationCenter.addObserver(observer, Notification.didReceiveNewMessages.id)
        notificationCenter.addObserver(observer, Notification.messagesDeleted.id)

        awaitClose {
            logger.debug { "close callback flow" }
            notificationCenter.removeObserver(observer, Notification.didReceiveNewMessages.id)
            notificationCenter.removeObserver(observer, Notification.messagesDeleted.id)
        }
    }
}