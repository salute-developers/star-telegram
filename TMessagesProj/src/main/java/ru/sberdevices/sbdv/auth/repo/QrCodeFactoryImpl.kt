package ru.sberdevices.sbdv.auth.repo

import android.content.Context
import android.graphics.Bitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import ru.sberdevices.sbdv.util.SbdvQRCodeWriter

class QrCodeFactoryImpl(private val context: Context) : AuthRepositoryImpl.QrCodeFactory {

    override fun create(text: String, reusable: Bitmap?): Bitmap {
        @Suppress("ReplaceWithEnumMap")
        val hints: HashMap<EncodeHintType, Any> = HashMap()
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
        hints[EncodeHintType.MARGIN] = 0
        val writer = SbdvQRCodeWriter()
        return writer.encode(text, 400, 400, hints, reusable)
    }
}