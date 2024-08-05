package ru.sberdevices.telegramcalls.vendor.contacts.domain.entity

import org.telegram.tgnet.TLRPC
import java.io.File

/**
 * @author Ирина Карпенко on 21.10.2021
 */
sealed class ContactsNotification {
    object Refresh : ContactsNotification()
    data class ReceiveUserAvatar(val file: File) : ContactsNotification()
    data class ChangeUserAvatar(val user: TLRPC.User) : ContactsNotification()
    data class ChangeUserName(val user: TLRPC.User) : ContactsNotification()
    data class AddUser(val user: TLRPC.User) : ContactsNotification()
    data class RemoveUser(val user: TLRPC.User) : ContactsNotification()
}