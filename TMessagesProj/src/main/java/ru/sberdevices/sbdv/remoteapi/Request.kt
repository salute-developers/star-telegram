package ru.sberdevices.sbdv.remoteapi

import androidx.annotation.AnyThread
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import java.util.EnumSet

data class Request(
    val tlObject: TLObject,
    val flags: EnumSet<Flag> = EnumSet.noneOf(Flag::class.java),
    val datacenterId: Int = ConnectionsManager.DEFAULT_DATACENTER_ID
) {

    enum class Flag {
        ENABLE_UNAUTHORIZED,
        FAIL_ON_SERVER_ERRORS,
        CAN_COMPRESS,
        WITHOUT_LOGIN,
        TRY_DIFFERENT_DC,
        FORCE_DOWNLOAD,
        INVOKE_AFTER,
        NEED_QUICK_ACK,
    }

    @AnyThread
    fun interface Sender {

        @Throws(Exception::class)
        suspend fun send(request: Request): Response

        class Exception(val tlError: TLRPC.TL_error) : kotlin.Exception("Code ${tlError.code}: ${tlError.text}")
    }
}

data class Response(val tlObject: TLObject)
