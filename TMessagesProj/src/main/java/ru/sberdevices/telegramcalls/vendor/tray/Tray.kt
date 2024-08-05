@file:Suppress("TooGenericExceptionCaught")
package ru.sberdevices.telegramcalls.vendor.tray

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import com.sdkit.base.core.threading.coroutines.CoroutineDispatchers
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.sbdv.config.Config
import ru.sberdevices.telegramcalls.vendor.recentapps.data.RecentAppsRepository
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author Ирина Карпенко on 01.12.2021
 */
interface Tray {
    suspend fun join()
    suspend fun leave()
}

internal class TrayImpl(
    private val recentAppsRepository: RecentAppsRepository,
    private val config: Config,
    private val coroutineDispatchers: CoroutineDispatchers
) : Tray {
    private val logger by Logger.lazy<TrayImpl>()
    private val joined = AtomicBoolean(false)

    override suspend fun join() = withContext(coroutineDispatchers.io) {
        try {
            if (config.integratedWithSingleCallsPlace) {
                if (joined.getAndSet(true)) {
                    logger.debug { "already joined tray, skip" }
                } else {
                    logger.debug { "join tray" }
                    recentAppsRepository.joinRecentApps()
                }
            }
        } catch (exception: CancellationException) {
            logger.debug { "join tray cancelled" }
            throw exception
        } catch (exception: Exception) {
            logger.warn(exception) { "join tray error" }
        } finally {
            logger.debug { "join tray completed" }
        }
    }

    override suspend fun leave() = withContext(coroutineDispatchers.io) {
        try {
            val wasJoined = joined.getAndSet(false)
            if (wasJoined) {
                logger.debug { "leave tray" }
                recentAppsRepository.leaveRecentApps()
            } else {
                if (config.integratedWithSingleCallsPlace) {
                    logger.debug { "already left tray, skip" }
                } else {
                    logger.debug { "is not integrated with single calls place, skip leaving tray" }
                }
            }
        } catch (exception: CancellationException) {
            logger.debug { "leave tray cancelled" }
            throw exception
        } catch (exception: Exception) {
            logger.warn(exception) { "leave tray error" }
        } finally {
            logger.debug { "leave tray completed" }
        }
    }
}