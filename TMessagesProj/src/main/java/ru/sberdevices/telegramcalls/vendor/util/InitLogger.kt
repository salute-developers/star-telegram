package ru.sberdevices.telegramcalls.vendor.util

import ru.sberdevices.common.logger.AndroidLoggerDelegate
import ru.sberdevices.common.logger.BuildConfig
import ru.sberdevices.common.logger.Logger

private const val LOGS_PREFIX = "TelegramCalls/"

/**
 * @author Ирина Карпенко on 20.10.2021
 */
fun initLogger() {
    Logger.setDelegates(
        AndroidLoggerDelegate(
            allowLogSensitive = BuildConfig.DEBUG,
            prefix = LOGS_PREFIX
        )
    )
}