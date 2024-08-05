package ru.sberdevices.sbdv.remoteapi

import kotlinx.coroutines.suspendCancellableCoroutine
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.RequestDelegateTimestamp
import org.telegram.tgnet.TLObject
import ru.sberdevices.common.logger.Logger
import java.util.EnumSet
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val ENUM_FLAG_TO_INT = hashMapOf(
    Request.Flag.CAN_COMPRESS to ConnectionsManager.RequestFlagCanCompress,
    Request.Flag.ENABLE_UNAUTHORIZED to ConnectionsManager.RequestFlagEnableUnauthorized,
    Request.Flag.FAIL_ON_SERVER_ERRORS to ConnectionsManager.RequestFlagFailOnServerErrors,
    Request.Flag.CAN_COMPRESS to ConnectionsManager.RequestFlagCanCompress,
    Request.Flag.WITHOUT_LOGIN to ConnectionsManager.RequestFlagWithoutLogin,
    Request.Flag.TRY_DIFFERENT_DC to ConnectionsManager.RequestFlagTryDifferentDc,
    Request.Flag.FORCE_DOWNLOAD to ConnectionsManager.RequestFlagForceDownload,
    Request.Flag.INVOKE_AFTER to ConnectionsManager.RequestFlagInvokeAfter,
    Request.Flag.NEED_QUICK_ACK to ConnectionsManager.RequestFlagNeedQuickAck,
)

fun interface SendFunction {
    fun send(
        tlObject: TLObject, delegate: RequestDelegateTimestamp, flags: Int, connectionType: Int, datacenterId: Int
    ): Int
}

fun interface CancelFunction {
    fun cancel(requestToken: Int)
}

class RequestSenderImpl(
    private val sendFunction: SendFunction,
    private val cancelFunction: CancelFunction,
) : Request.Sender {

    private val logger = Logger.get("RequestSenderImpl")

    @Throws(Request.Sender.Exception::class)
    override suspend fun send(request: Request): Response = suspendCancellableCoroutine { continuation ->
        logger.verbose { "send() with $request" }

        val requestToken = sendFunction.send(request.tlObject, { response, error, _ ->
            if (error == null) {
                logger.verbose { "Got response for $request" }
                continuation.resume(Response(response))
            } else if (error.code == 401 || error.text.contains("SESSION_PASSWORD_NEEDED")) {
                logger.verbose { "Got ${error.text} error, code: ${error.code}" }
                continuation.resume(Response(error))
            } else {
                logger.verbose { "Got error for $request: $error" }
                continuation.resumeWithException(Request.Sender.Exception(error))
            }
        }, request.flags.toInt(), ConnectionsManager.ConnectionTypeGeneric, request.datacenterId)

        continuation.invokeOnCancellation {
            logger.verbose { "Cancel request with token $requestToken" }
            cancelFunction.cancel(requestToken)
        }
    }
}

private fun EnumSet<Request.Flag>.toInt(): Int {
    var intFlags = 0
    for (flag in this) {
        intFlags = intFlags or flag.toInt()
    }
    return intFlags
}

private fun Request.Flag.toInt(): Int = ENUM_FLAG_TO_INT[this]!!
