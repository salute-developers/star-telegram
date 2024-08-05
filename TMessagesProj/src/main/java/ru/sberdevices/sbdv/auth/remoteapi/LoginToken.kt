package ru.sberdevices.sbdv.auth.remoteapi

import org.telegram.tgnet.TLRPC
import java.util.Date

sealed class LoginToken {

    data class QrCode(val expires: Date, val token: ByteArray) : LoginToken() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as QrCode

            if (expires != other.expires) return false
            if (!token.contentEquals(other.token)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = expires.hashCode()
            result = 31 * result + token.contentHashCode()
            return result
        }
    }

    data class Success(val authorization: TLRPC.TL_auth_authorization) : LoginToken()

    data class MigrateTo(val datacenterId: Int, val token: ByteArray) : LoginToken() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as MigrateTo

            if (datacenterId != other.datacenterId) return false
            if (!token.contentEquals(other.token)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = datacenterId
            result = 31 * result + token.contentHashCode()
            return result
        }
    }

    object SessionPasswordNeeded : LoginToken()
}
