package ru.sberdevices.sbdv.auth.view

import android.graphics.Bitmap
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.sbdv.auth.repo.AuthRepository
import ru.sberdevices.sbdv.auth.repo.QrCodeLoginUpdate

@MainThread
class QrLoginViewController(
    private val view: View,
    private val repo: AuthRepository,
    mainDispatcher: CoroutineDispatcher,
    private val onLoginSuccessListener: OnLoginSuccessListener
) {
    private val logger = Logger.get("QrLoginViewController")

    @VisibleForTesting
    val scope = CoroutineScope(mainDispatcher + SupervisorJob())

    @VisibleForTesting
    var observeQrCodeLogin: Job? = null

    val leftForPhoneLogin: Boolean get() = view.leftForPhoneLogin()

    @MainThread
    interface View {
        fun showQr(qrCode: Bitmap)
        fun hideQr()
        fun show2faPage()
        fun leftForPhoneLogin(): Boolean
    }

    @MainThread
    fun interface OnLoginSuccessListener {
        fun onSuccess(token: LoginToken)
    }

    fun onResume() {
        logger.info { "onResume()@${hashCode()}" }

        observeQrCodeLogin = repo.observeQrCodeLogin()
            .onEach(::handleUpdate)
            .launchIn(scope)
    }

    fun onPause() {
        logger.info { "onPause()@${hashCode()}" }

        observeQrCodeLogin?.cancel()
        observeQrCodeLogin = null
    }

    fun onDestroy() {
        logger.info { "onDestroy()@${hashCode()}" }

        scope.cancel()
    }

    private fun handleUpdate(update: QrCodeLoginUpdate) {
        logger.info { "handleUpdate() with ${update.javaClass.simpleName}" }

        when (update) {
            is QrCodeLoginUpdate.NewQrCode -> view.showQr(update.code)
            is QrCodeLoginUpdate.LoginSuccess -> {
                val loginToken = LoginToken(update.authorization)
                onLoginSuccessListener.onSuccess(loginToken)
            }
            is QrCodeLoginUpdate.NoNetwork -> view.hideQr()
            is QrCodeLoginUpdate.NeedPassword -> view.show2faPage()
        }
    }
}