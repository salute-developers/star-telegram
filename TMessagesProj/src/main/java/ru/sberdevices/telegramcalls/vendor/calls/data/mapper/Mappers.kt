package ru.sberdevices.telegramcalls.vendor.calls.data.mapper

import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import ru.sberdevices.starcontacts.api.vendor.calls.entity.CallDraft
import ru.sberdevices.starcontacts.entity.CallDirection
import ru.sberdevices.starcontacts.entity.CallStatus
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

/**
 * @author Ирина Карпенко on 21.10.2021
 */
internal fun TLRPC.messages_Messages.toCallDrafts(currentAccount: Int): List<CallDraft> {
    return messages.indices
        .map { messages[it] }
        .filterNot { message -> message.action == null || message.action is TLRPC.TL_messageActionHistoryClear }
        .map { message -> message.toCallDraft(currentAccount) }
}

internal fun TLRPC.Message.toCallDraft(currentAccount: Int): CallDraft {
    val isOutgoingCall = isOutgoingCall(currentAccount)
    return CallDraft(
        contactId = determineContactId(isOutgoingCall),
        additionalParams = null,
        direction = determineCallDirection(isOutgoingCall),
        status = determineCallStatus(isOutgoingCall),
        isVideoCall = action != null && action.video,
        timestamp = LocalDateTime.ofInstant(
            Date(date * 1000L).toInstant(),
            ZoneId.systemDefault()
        )
    )
}

internal fun TLRPC.Message.determineContactId(isOutgoingCall: Boolean): String {
    val userId = if (isOutgoingCall) peer_id.user_id else from_id.user_id
    return userId.toString()
}

internal fun TLRPC.Message.determineCallStatus(isOutgoingCall: Boolean): CallStatus {
    return when {
        isOutgoingCall -> CallStatus.UNKNOWN
        isMissedCall() -> CallStatus.MISSED
        else -> CallStatus.ACCEPTED
    }
}

internal fun determineCallDirection(isOutgoingCall: Boolean): CallDirection {
    return if (isOutgoingCall) CallDirection.OUTGOING else CallDirection.INCOMING
}

internal fun TLRPC.Message.isOutgoingCall(currentAccount: Int): Boolean {
    return from_id.user_id == UserConfig.getInstance(currentAccount).getClientUserId()
}

internal fun TLRPC.Message.isMissedCall(): Boolean {
    return action.reason is TLRPC.TL_phoneCallDiscardReasonMissed || action.reason is TLRPC.TL_phoneCallDiscardReasonBusy
}