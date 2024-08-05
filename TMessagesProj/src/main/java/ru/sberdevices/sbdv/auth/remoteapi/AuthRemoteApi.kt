package ru.sberdevices.sbdv.auth.remoteapi

import ru.sberdevices.sbdv.remoteapi.Request

/**
 * Remote API described here: https://core.telegram.org/api/qr-login
 */
interface AuthRemoteApi {

    /**
     * Just waits for a successful login.
     */
    suspend fun awaitLoginSuccess()

    @Throws(UnexpectedResponseException::class, Request.Sender.Exception::class)
    suspend fun exportLoginToken(): LoginToken

    @Throws(UnexpectedResponseException::class, Request.Sender.Exception::class)
    suspend fun importLoginToken(migrationToken: LoginToken.MigrateTo): LoginToken.Success
}
