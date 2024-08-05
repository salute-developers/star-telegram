package ru.sberdevices.sbdv.auth.repo

import android.graphics.Bitmap
import androidx.annotation.AnyThread
import kotlinx.coroutines.flow.Flow
import org.telegram.tgnet.TLRPC

@AnyThread
interface AuthRepository {

    fun observeQrCodeLogin(): Flow<QrCodeLoginUpdate>
}

sealed class QrCodeLoginUpdate {

    data class NewQrCode(val code: Bitmap) : QrCodeLoginUpdate()

    data class LoginSuccess(val authorization: TLRPC.TL_auth_authorization) : QrCodeLoginUpdate()

    object NoNetwork : QrCodeLoginUpdate()

    object NeedPassword : QrCodeLoginUpdate()
}
