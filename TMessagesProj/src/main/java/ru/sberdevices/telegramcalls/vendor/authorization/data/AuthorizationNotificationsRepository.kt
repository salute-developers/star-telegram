package ru.sberdevices.telegramcalls.vendor.authorization.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.telegramcalls.vendor.authorization.domain.entity.AuthorizationNotification
import ru.sberdevices.telegramcalls.vendor.notifications.data.Notification
import java.io.File

/**
 * @author Ирина Карпенко on 21.10.2021
 */
interface AuthorizationNotificationsRepository {
    val notifications: Flow<AuthorizationNotification>
}

internal class AuthorizationNotificationsRepositoryImpl : AuthorizationNotificationsRepository {
    private val logger by Logger.lazy<AuthorizationNotificationsRepositoryImpl>()

    private val userConfig: UserConfig
        get() = UserConfig.getInstance(UserConfig.selectedAccount)

    override val notifications: Flow<AuthorizationNotification> = callbackFlow {
        val notificationCenter: NotificationCenter = NotificationCenter.getInstance(UserConfig.selectedAccount)
        val observer = NotificationCenter.NotificationCenterDelegate { id, _, args ->
            logger.debug { "received notification ${Notification.get(id)}" }
            when (id) {
                Notification.appDidLogout.id,
                Notification.mainUserInfoChanged.id,
                Notification.contactsDidLoad.id -> launch {
                    logger.debug { "send(AuthorizationNotification.RefreshProfiles)" }
                    send(AuthorizationNotification.RefreshProfiles)
                }
                Notification.fileDidLoad.id -> {
                    val location = args[0] as? String
                    val file = args[1] as? File
                    if (!location.isNullOrBlank() && file != null && file.exists()) {
                        launch {
                            val currentUserAvatarKey = userConfig.currentUser?.photo?.photo_big?.volume_id?.toString()
                            if (!currentUserAvatarKey.isNullOrBlank() && currentUserAvatarKey in location) {
                                send(AuthorizationNotification.ReceiveUserAvatar(file))
                            }
                        }
                    }
                }
                Notification.SbdvOnUserAvatarChanged.id -> {
                    val user = args[0] as? TLRPC.User
                    if (user != null) launch { send(AuthorizationNotification.ChangeUserAvatar(user)) }
                }
                Notification.SbdvOnUserNameChanged.id -> {
                    val user = args[0] as? TLRPC.User
                    if (user != null) launch { send(AuthorizationNotification.ChangeUserName(user)) }
                }
            }
        }
        notificationCenter.addObserver(observer, Notification.appDidLogout.id)
        notificationCenter.addObserver(observer, Notification.mainUserInfoChanged.id)
        notificationCenter.addObserver(observer, Notification.contactsDidLoad.id)
        notificationCenter.addObserver(observer, Notification.fileDidLoad.id)
        notificationCenter.addObserver(observer, Notification.SbdvOnUserAvatarChanged.id)
        notificationCenter.addObserver(observer, Notification.SbdvOnUserNameChanged.id)

        awaitClose {
            logger.debug { "close callback flow" }
            notificationCenter.removeObserver(observer, Notification.appDidLogout.id)
            notificationCenter.removeObserver(observer, Notification.mainUserInfoChanged.id)
            notificationCenter.removeObserver(observer, Notification.contactsDidLoad.id)
            notificationCenter.removeObserver(observer, Notification.fileDidLoad.id)
            notificationCenter.removeObserver(observer, Notification.SbdvOnUserAvatarChanged.id)
            notificationCenter.removeObserver(observer, Notification.SbdvOnUserNameChanged.id)
        }
    }
}