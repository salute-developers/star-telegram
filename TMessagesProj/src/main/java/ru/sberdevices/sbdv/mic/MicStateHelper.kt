package ru.sberdevices.sbdv.mic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sdkit.base.core.threading.coroutines.CoroutineDispatchers
import com.sdkit.core.logging.domain.LoggerFactory
import ru.sberdevices.services.mic.camera.state.MicCameraStateRepository
import java.util.concurrent.CopyOnWriteArraySet

class MicStateHelper(
    micCameraStateRepository: MicCameraStateRepository,
    applicationScope: CoroutineScope,
    coroutineDispatchers: CoroutineDispatchers,
    loggerFactory: LoggerFactory
) {

    private val logger = loggerFactory.get("MicStateHelper")

    private val listeners = CopyOnWriteArraySet<Listener>()

    private var isMicEnabled: Boolean = false

    init {
        applicationScope.launch {
            micCameraStateRepository.micState
                .distinctUntilChanged()
                .collect { state ->
                    isMicEnabled = state == MicCameraStateRepository.State.ENABLED
                    withContext(coroutineDispatchers.ui) {
                        listeners.forEach { it.onMicStateChanged(isMicEnabled) }
                    }
                    logger.d { "notify listeners, isMicEnabled = $isMicEnabled, count = ${listeners.count()}" }
                }
        }
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
        logger.d { "add listener = $listener, count = ${listeners.count()}" }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
        logger.d { "remove listener = $listener, count = ${listeners.count()}" }
    }

    fun isMicEnabled(): Boolean {
        logger.d { "isMicEnabled = $isMicEnabled" }
        return isMicEnabled
    }

    interface Listener {
        fun onMicStateChanged(isEnabled: Boolean)
    }
}