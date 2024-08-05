package ru.sberdevices.telegramcalls.vendor.contacts.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.telegramcalls.vendor.contacts.domain.entity.ContactsNotification
import ru.sberdevices.telegramcalls.vendor.notifications.data.Notification
import java.io.File

/**
 * @author Ирина Карпенко on 21.10.2021
 */
interface ContactsNotificationsRepository {
    val notifications: Flow<ContactsNotification>
}

internal class ContactsNotificationsRepositoryImpl : ContactsNotificationsRepository {
    private val logger by Logger.lazy<ContactsNotificationsRepositoryImpl>()

    override val notifications: Flow<ContactsNotification> = callbackFlow {
        val notificationCenter: NotificationCenter = NotificationCenter.getInstance(UserConfig.selectedAccount)
        val observer = NotificationCenter.NotificationCenterDelegate { id, _, args ->
            logger.debug { "received notification ${Notification.get(id)}" }
            when (id) {
                Notification.contactsDidLoad.id -> {
                    launch { send(ContactsNotification.Refresh) }
                }
                Notification.fileDidLoad.id -> {
                    val location = (args[0] as? String)
                    val file = args[1] as? File
                    if (!location.isNullOrBlank() && file != null && file.exists()) {
                        launch {
                            send(ContactsNotification.ReceiveUserAvatar(file))
                        }
                    }
                }
                Notification.SbdvOnUserAvatarChanged.id -> {
                    val user = args[0] as? TLRPC.User
                    if (user != null) launch { send(ContactsNotification.ChangeUserAvatar(user)) }
                }
                Notification.SbdvOnUserNameChanged.id -> {
                    val user = args[0] as? TLRPC.User
                    if (user != null) launch { send(ContactsNotification.ChangeUserName(user)) }
                }
                Notification.SbdvOnUserAdded.id -> {
                    val user = args[0] as? TLRPC.User
                    if (user != null) launch { send(ContactsNotification.AddUser(user)) }
                }
                Notification.SbdvOnUserRemoved.id -> {
                    val user = args[0] as? TLRPC.User
                    if (user != null) launch { send(ContactsNotification.RemoveUser(user)) }
                }
            }
        }
        notificationCenter.addObserver(observer, Notification.contactsDidLoad.id)
        notificationCenter.addObserver(observer, Notification.fileDidLoad.id)
        notificationCenter.addObserver(observer, Notification.SbdvOnUserAvatarChanged.id)
        notificationCenter.addObserver(observer, Notification.SbdvOnUserNameChanged.id)
        notificationCenter.addObserver(observer, Notification.SbdvOnUserAdded.id)
        notificationCenter.addObserver(observer, Notification.SbdvOnUserRemoved.id)

        awaitClose {
            logger.debug { "close callback flow" }
            notificationCenter.removeObserver(observer, Notification.contactsDidLoad.id)
            notificationCenter.removeObserver(observer, Notification.fileDidLoad.id)
            notificationCenter.removeObserver(observer, Notification.SbdvOnUserAvatarChanged.id)
            notificationCenter.removeObserver(observer, Notification.SbdvOnUserNameChanged.id)
            notificationCenter.removeObserver(observer, Notification.SbdvOnUserAdded.id)
            notificationCenter.removeObserver(observer, Notification.SbdvOnUserRemoved.id)
        }
    }
}