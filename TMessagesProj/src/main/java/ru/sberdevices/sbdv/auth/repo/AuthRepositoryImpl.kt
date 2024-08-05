package ru.sberdevices.sbdv.auth.repo

import android.graphics.Bitmap
import androidx.annotation.AnyThread
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.sbdv.auth.remoteapi.AuthRemoteApi
import ru.sberdevices.sbdv.auth.remoteapi.LoginToken
import ru.sberdevices.sbdv.network.NetworkRepository
import ru.sberdevices.sbdv.network.NetworkState
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The internal logic described here: https://core.telegram.org/api/qr-login
 */
@AnyThread
class AuthRepositoryImpl(
    private val authApi: AuthRemoteApi,
    private val ioDispatcher: CoroutineDispatcher,
    private val qrCodeFactory: QrCodeFactory,
    private val networkRepository: NetworkRepository
) : AuthRepository {

    private val logger = Logger.get("AuthRepositoryImpl")

    private var is2faPasswordInput = AtomicBoolean(false)

    @AnyThread
    fun interface QrCodeFactory {
        fun create(text: String, reusable: Bitmap?): Bitmap
    }

    override fun observeQrCodeLogin(): Flow<QrCodeLoginUpdate> {
        return listOf(observeQrCodeUpdate(), observeLoginSuccess()).merge()
    }

    private fun observeQrCodeUpdate(): Flow<QrCodeLoginUpdate> = flow {
        while (currentCoroutineContext().isActive) {
            val hasNetwork = networkRepository.networkState.first() == NetworkState.AVAILABLE
            if (!hasNetwork) {
                emit(QrCodeLoginUpdate.NoNetwork)
            }
            if (is2faPasswordInput.get()) return@flow

            when (val loginToken = authApi.exportLoginToken()) {
                is LoginToken.QrCode -> {
                    handleUpdateQr(loginToken)
                }
                is LoginToken.SessionPasswordNeeded -> {
                    handleSessionPasswordNeeded()
                }
                else -> {
                    handleMigrationOrSuccess(loginToken)
                    break
                }
            }
        }
    }.flowOn(ioDispatcher)

    private fun observeLoginSuccess(): Flow<QrCodeLoginUpdate> = flow {
        if (!is2faPasswordInput.get()) {
            authApi.awaitLoginSuccess()
            val loginToken = authApi.exportLoginToken()
            handleMigrationOrSuccess(loginToken)
        }
    }

    private fun createBitmap(loginToken: LoginToken.QrCode): Bitmap {
        val encoded = encodeToken(loginToken.token)
        return qrCodeFactory.create(encoded, null)
    }

    private suspend fun FlowCollector<QrCodeLoginUpdate>.handleMigrationOrSuccess(loginToken: LoginToken) {
        when (loginToken) {
            is LoginToken.MigrateTo -> {
                val success = authApi.importLoginToken(loginToken)
                emit(QrCodeLoginUpdate.LoginSuccess(success.authorization))
            }
            is LoginToken.SessionPasswordNeeded -> {
                handleSessionPasswordNeeded()
            }
            is LoginToken.Success -> emit(QrCodeLoginUpdate.LoginSuccess(loginToken.authorization))
            else -> logger.warn { "Unexpected $loginToken, was expecting MigrateTo or Success" }
        }
    }

    private suspend fun FlowCollector<QrCodeLoginUpdate>.handleUpdateQr(loginToken: LoginToken.QrCode) {
        if (is2faPasswordInput.get()) return

        val bitmap = createBitmap(loginToken)
        emit(QrCodeLoginUpdate.NewQrCode(bitmap))

        val delayMs = loginToken.expires.time - System.currentTimeMillis()
        logger.verbose { "Will delay for $delayMs ms" }
        delay(delayMs)
    }

    private suspend fun FlowCollector<QrCodeLoginUpdate>.handleSessionPasswordNeeded() {
        logger.verbose { "handlePasswordNeeded" }
        is2faPasswordInput.set(true)
        emit(QrCodeLoginUpdate.NeedPassword)
    }
}

private fun encodeToken(bytes: ByteArray): String {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    return "tg://login?token=" + encoder.encodeToString(bytes)
}
