package ru.sberdevices.sbdv.camera

import androidx.annotation.MainThread
import com.sdkit.base.core.threading.coroutines.CoroutineDispatchers
import com.sdkit.core.logging.domain.LoggerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.sberdevices.services.mic.camera.state.MicCameraStateRepository
import java.util.concurrent.CopyOnWriteArraySet

@MainThread
class CameraAvailabilityHelper(
    micCameraStateRepository: MicCameraStateRepository,
    applicationScope: CoroutineScope,
    coroutineDispatchers: CoroutineDispatchers,
    loggerFactory: LoggerFactory
) {

    private val logger = loggerFactory.get("CameraAvailabilityHelper")

    private val listeners = CopyOnWriteArraySet<Listener>()

    @Volatile
    private var isCameraAvailable: Boolean = false

    init {
        applicationScope.launch {
            micCameraStateRepository.cameraState
                .combine(micCameraStateRepository.isCameraCovered) { state, isCovered ->
                    state == MicCameraStateRepository.State.ENABLED && !isCovered
                }
                .distinctUntilChanged()
                .collect { isAvailable ->
                    isCameraAvailable = isAvailable
                    withContext(coroutineDispatchers.ui) {
                        listeners.forEach { it.onAvailabilityChange(isAvailable) }
                    }
                    logger.d { "notify listeners, isCameraAvailability = ${isAvailable}, count = ${listeners.count()}" }
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

    fun isAvailable(): Boolean {
        logger.d { "isAvailable = $isCameraAvailable" }
        return isCameraAvailable
    }

    @MainThread
    fun interface Listener {
        fun onAvailabilityChange(isAvailable: Boolean)
    }
}
