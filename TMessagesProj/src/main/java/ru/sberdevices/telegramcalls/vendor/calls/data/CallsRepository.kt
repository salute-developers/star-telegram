package ru.sberdevices.telegramcalls.vendor.calls.data

import kotlinx.coroutines.suspendCancellableCoroutine
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.starcontacts.api.vendor.calls.entity.CallDraft
import ru.sberdevices.telegramcalls.vendor.calls.data.mapper.toCallDrafts
import kotlin.coroutines.resumeWithException

/**
 * @author Ирина Карпенко on 20.10.2021
 */
interface CallsRepository {
    suspend fun requestRecentCalls(count: Int): List<CallDraft>
}

internal class CallsRepositoryImpl : CallsRepository {
    private val logger by Logger.lazy<CallsRepositoryImpl>()

    override suspend fun requestRecentCalls(count: Int) = suspendCancellableCoroutine<List<CallDraft>> { continuation ->
        logger.debug { "requestRecentCalls(count=$count)" }
        val currentAccount = UserConfig.selectedAccount
        val requestId = ConnectionsManager.getInstance(currentAccount).sendRequest(
            createTlRPCMessagesSearchRequest(count),
            { response: TLObject, error: TLRPC.TL_error? ->
                if (error == null) {
                    val messages = (response as TLRPC.messages_Messages).toCallDrafts(currentAccount)
                    logger.debug { "got ${messages.count()} calls" }
                    continuation.resume(messages) {
                        logger.debug { "request recent calls cancelled" }
                    }
                } else {
                    logger.debug { "request recent calls error" }
                    continuation.resumeWithException(Throwable())
                }
            },
            ConnectionsManager.RequestFlagFailOnServerErrors
        )
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(requestId, ConnectionsManager.generateClassGuid())

        continuation.invokeOnCancellation {
            logger.debug { "request recent calls cancelled" }
        }
    }
}

private fun createTlRPCMessagesSearchRequest(count: Int): TLRPC.TL_messages_search {
    return TLRPC.TL_messages_search().apply {
        limit = count
        peer = TLRPC.TL_inputPeerEmpty()
        filter = TLRPC.TL_inputMessagesFilterPhoneCalls()
        q = ""
        offset_id = 0
    }
}
