package ru.sberdevices.telegramcalls.vendor

import android.app.Service
import android.content.Intent
import android.os.IBinder
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.sbdv.SbdvServiceLocator

/**
 * Сервис для межпроцессного взаимодействия с единым звонковым приложением
 *
 * @author Ирина Карпенко on 03.09.2021
 */
class VendorService : Service() {

    private val logger by Logger.lazy<VendorService>()
    private val service: VendorServiceImpl = SbdvServiceLocator.getVendorServiceImpl()

    override fun onCreate() {
        super.onCreate()
        logger.debug { "onCreate" }
    }

    override fun onDestroy() {
        logger.debug { "onDestroy" }
        service.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        logger.debug { "onBind: $intent" }
        service.refreshProfiles()
        return service
    }
}