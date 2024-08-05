package ru.sberdevices.telegramcalls.vendor.calls.domain.entity

import ru.sberdevices.starcontacts.api.vendor.calls.entity.CallDraft

/**
 * @author Ирина Карпенко on 21.10.2021
 */
sealed class CallsNotification {
    object Refresh : CallsNotification()
    data class NewCalls(val calls: List<CallDraft>) : CallsNotification()
}
