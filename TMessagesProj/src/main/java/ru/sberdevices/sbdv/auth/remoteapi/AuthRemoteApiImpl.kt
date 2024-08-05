package ru.sberdevices.sbdv.auth.remoteapi

import kotlinx.coroutines.suspendCancellableCoroutine
import org.telegram.messenger.BuildVars
import org.telegram.messenger.MessagesController
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.TL_auth_exportLoginToken
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.sbdv.remoteapi.Request
import java.util.Date
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class AuthRemoteApiImpl(
    private val sender: Request.Sender,
    private val callbackManager: CallbackManager
) : AuthRemoteApi {

    private val logger = Logger.get("AuthRemoteApiImpl")

    interface CallbackManager {

        fun addLoginTokenUpdateCallback(callback: MessagesController.LoginTokenUpdateCallback)

        fun removeLoginTokenUpdateCallback(callback: MessagesController.LoginTokenUpdateCallback)
    }

    override suspend fun awaitLoginSuccess(): Unit = suspendCancellableCoroutine { continuation ->
        val callback: MessagesController.LoginTokenUpdateCallback =
            object : MessagesController.LoginTokenUpdateCallback {
                override fun onLoginTokenUpdate() {
                    callbackManager.removeLoginTokenUpdateCallback(this)
                    continuation.resume(Unit)
                }
            }

        callbackManager.addLoginTokenUpdateCallback(callback)

        continuation.invokeOnCancellation {
            logger.verbose { "Cancel awaitLoginSuccess()" }
            callbackManager.removeLoginTokenUpdateCallback(callback)
        }
    }

    @Throws(UnexpectedResponseException::class, Request.Sender.Exception::class)
    override suspend fun exportLoginToken(): LoginToken {
        logger.verbose { "exportLoginToken()" }

        val request = Request(
            tlObject = TL_auth_exportLoginToken().apply {
                api_hash = BuildVars.APP_HASH
                api_id = BuildVars.APP_ID
            },
            flags = EnumSet.of(
                Request.Flag.FAIL_ON_SERVER_ERRORS,
                Request.Flag.WITHOUT_LOGIN,
                Request.Flag.TRY_DIFFERENT_DC,
                Request.Flag.ENABLE_UNAUTHORIZED,
            )
        )

        val response = sender.send(request)
        return when (val tlObject = response.tlObject) {
            is TLRPC.TL_auth_loginToken -> {
                val expires = Date(TimeUnit.SECONDS.toMillis(tlObject.expires.toLong()))
                LoginToken.QrCode(expires = expires, token = tlObject.token)
            }
            is TLRPC.TL_auth_loginTokenMigrateTo -> {
                LoginToken.MigrateTo(tlObject.dc_id, tlObject.token)
            }
            is TLRPC.TL_auth_loginTokenSuccess -> {
                LoginToken.Success(authorization = tlObject.authorization as TLRPC.TL_auth_authorization)
            }
            is TLRPC.TL_error -> {
                if (is2faError(tlObject)) LoginToken.SessionPasswordNeeded
                else throw UnexpectedResponseException(tlObject)
            }
            else -> throw UnexpectedResponseException(tlObject)
        }
    }

    // TODO test migration between DC's somehow
    @Throws(UnexpectedResponseException::class, Request.Sender.Exception::class)
    override suspend fun importLoginToken(migrationToken: LoginToken.MigrateTo): LoginToken.Success {
        logger.verbose { "importLoginToken()" }

        val request = Request(
            tlObject = TLRPC.TL_auth_importLoginToken().apply {
                token = migrationToken.token
            },
            flags = EnumSet.of(
                Request.Flag.FAIL_ON_SERVER_ERRORS,
                Request.Flag.WITHOUT_LOGIN,
                Request.Flag.ENABLE_UNAUTHORIZED,
            ),
            datacenterId = migrationToken.datacenterId
        )

        val response = sender.send(request)
        return when (val tlObject = response.tlObject) {
            is TLRPC.TL_auth_loginTokenSuccess -> {
                LoginToken.Success(authorization = tlObject.authorization as TLRPC.TL_auth_authorization)
            }
            else -> throw UnexpectedResponseException(tlObject)
        }
    }

    private fun is2faError(tlError: TLRPC.TL_error): Boolean{
        val isPasswordNeeded = tlError.code == 401 || tlError.text.contains("SESSION_PASSWORD_NEEDED")
        logger.verbose { "is2faError: $isPasswordNeeded" }
        return isPasswordNeeded
    }
}
