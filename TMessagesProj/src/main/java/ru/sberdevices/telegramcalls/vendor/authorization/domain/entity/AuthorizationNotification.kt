package ru.sberdevices.telegramcalls.vendor.authorization.domain.entity

import org.telegram.tgnet.TLRPC
import java.io.File

/**
 * @author Ирина Карпенко on 21.10.2021
 */
sealed class AuthorizationNotification {
    object RefreshProfiles : AuthorizationNotification()
    data class ReceiveUserAvatar(val file: File) : AuthorizationNotification()
    data class ChangeUserAvatar(val user: TLRPC.User) : AuthorizationNotification()
    data class ChangeUserName(val user: TLRPC.User) : AuthorizationNotification()
}