package ru.sberdevices.sbdv.network

import androidx.annotation.AnyThread
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.telegram.messenger.NotificationCenter
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ConnectionsManager.ConnectionStateConnected
import ru.sberdevices.common.logger.Logger

@AnyThread
class NetworkRepositoryImpl(
    private val notificationCenter: NotificationCenter,
    private val connectionsManager: ConnectionsManager
) : NetworkRepository {

    private val logger = Logger.get("NetworkRepositoryImpl")

    override val networkState: Flow<NetworkState> = callbackFlow {
        val connectionObserver = NotificationCenter.NotificationCenterDelegate { id, _, _ ->
            if (id == NotificationCenter.didUpdateConnectionState) {
                val isNetworkAvailable = connectionsManager.connectionState == ConnectionStateConnected

                logger.info { "didUpdateConnectionState received, now isNetworkAvailable = $isNetworkAvailable" }
                updateNetworkState(isNetworkAvailable)
            }
        }

        notificationCenter.addObserver(connectionObserver, NotificationCenter.didUpdateConnectionState)

        updateNetworkState(connectionsManager.connectionState == ConnectionStateConnected)

        awaitClose {
            logger.debug { "close callbackFlow" }
            notificationCenter.removeObserver(connectionObserver, NotificationCenter.didUpdateConnectionState)
        }
    }

    private fun ProducerScope<NetworkState>.updateNetworkState(isNetworkAvailable: Boolean) {
        logger.info { "updateNetworkState(isNetworkAvailable = $isNetworkAvailable)" }
        trySendBlocking(getNetworkState(isNetworkAvailable))
    }

    private fun getNetworkState(isNetworkAvailable: Boolean): NetworkState {
        return if (isNetworkAvailable) NetworkState.AVAILABLE else NetworkState.NOT_AVAILABLE
    }
}