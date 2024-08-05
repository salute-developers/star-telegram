@file:Suppress("TooGenericExceptionCaught")

package ru.sberdevices.telegramcalls.data.hdmi

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioDeviceInfo.TYPE_HDMI
import android.media.AudioManager
import android.media.AudioManager.GET_DEVICES_OUTPUTS
import android.media.AudioRouting
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.sdkit.base.core.threading.coroutines.CoroutineDispatchers
import com.sdkit.core.di.platform.AppContext
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.sbdv.config.Config
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * В версии аосп в 1.79 сделаны доработки, благодаря которым корректно работает вывод звука через hdmi и speaker.
 * В телеграм обработка вывода звука сделана под фича флагом forceHdmiEnabled: Boolean.
 * В сеттингах устройства есть настройка вывода звука. Их три варианта:
 * 1) авто
 * В случае если подключен авто, то при подключении и отключении провода hdmi, сработает MediaRouter.Callback.onChanged, и
 * если выбран hdmi то нужно вызывать на аудиотреке setPreferredDevice. То есть, при подключении провода звук должен
 * выводиться через TV, при отключении - через спикер.
 * 2) hdmi
 * Если пользователь выбрал эту настройку, то нужно всегда выводить звук через hdmi, даже если он не подключен.
 * 3) устройство
 * Если пользователь выбрал эту настройку, то нужно всегда выводить звук через спикер, даже если подключен hdmi.
 * Текущую настройку помимо ui настроек можно увидеть в adb:
 * adb shell settings list global | grep audio
 *
 * @author Irina Karpenko
 * @since 21.03.2022
 */
private const val HDMI_ROUTE_NAME = "HDMI"

interface HdmiRouteEnforcer {
    fun startListeningHdmiRoute()
    fun stopListeningHdmiRoute()
    fun setAudioRouting(audioRouting: AudioRouting)
    fun clearAudioRouting()
}

@RequiresApi(Build.VERSION_CODES.N)
class HdmiRouteEnforcerImpl(
    @AppContext private val context: Context,
    private val config: Config,
    private val coroutineDispatchers: CoroutineDispatchers
) : HdmiRouteEnforcer {
    private val logger = Logger.get("HdmiRouteEnforcerImpl")

    private val audioRouting: AtomicReference<AudioRouting?> = AtomicReference(null)
    private val listening: AtomicBoolean = AtomicBoolean(false)
    private val coroutineScope = CoroutineScope(
        SupervisorJob() + coroutineDispatchers.default + CoroutineExceptionHandler { coroutineContext, throwable ->
            logger.warn(throwable) { "coroutine $coroutineContext exception" }
        }
    )

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mediaRouter = MediaRouter.getInstance(context)
    private val selector = MediaRouteSelector.Builder()
        .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
        .build()

    private val mediaRouterCallback = object : MediaRouter.Callback() {
        // Вызывается только если в сеттингах устройства auto режим вывода звука
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            logger.debug { "onRouteChanged(${route.name})" }
            setPreferredAudioOutputDevice()
        }
    }

    override fun startListeningHdmiRoute() {
        logger.debug { "startListeningHdmiRoute()" }
        if (!config.forceHdmiEnabled) return

        if (listening.getAndSet(true)) {
            logger.debug { "already listening for media route changes, skip" }
        } else {
            logger.debug { "listen for media route changes" }
            coroutineScope.launch(coroutineDispatchers.ui) {
                mediaRouter.addCallback(selector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_UNFILTERED_EVENTS)
            }
        }
    }

    override fun stopListeningHdmiRoute() {
        logger.debug { "stopListeningHdmiRoute()" }
        val wasListening = listening.getAndSet(false)
        if (wasListening) {
            logger.debug { "stop listening for media route changes" }
            coroutineScope.launch(coroutineDispatchers.ui) { mediaRouter.removeCallback(mediaRouterCallback) }
        } else {
            logger.debug { "wasn't listening for media route changes, skip" }
        }
    }

    override fun setAudioRouting(audioRouting: AudioRouting) {
        logger.debug { "setAudioRouting()" }
        this.audioRouting.set(audioRouting)
        if (config.forceHdmiEnabled) {
            coroutineScope.launch(coroutineDispatchers.ui) {
                val route = mediaRouter.updateSelectedRoute(selector)
                logger.debug { "selected route: $route" }
                if (route.name == HDMI_ROUTE_NAME) setPreferredAudioOutputDevice()
            }
        } else {
            logger.debug { "force hdmi is not enabled, skip selecting hdmi output device" }
        }
    }

    override fun clearAudioRouting() {
        logger.debug { "clearAudioRouting()" }
        this.audioRouting.set(null)
    }

    private fun setPreferredAudioOutputDevice() {
        logger.debug { "setPreferredAudioOutputDevice()" }
        coroutineScope.launch {
            try {
                forceHdmiAudioSource()
            } catch (exception: CancellationException) {
                logger.debug { "force hdmi audio source cancelled" }
                throw exception
            } catch (exception: Exception) {
                logger.warn(exception) { "force hdmi audio source failed" }
            }
        }
    }

    private suspend fun forceHdmiAudioSource() {
        logger.debug { "forceHdmiAudioSource()" }
        waitForAudioRouting()
        audioRouting.get()
            ?.let { routing ->
                val hdmiDevice = getHdmiDevice()
                if (hdmiDevice == null) {
                    logger.debug { "no HDMI audio device found" }
                } else {
                    try {
                        if (!routing.setPreferredDevice(hdmiDevice)) {
                            logger.error { "error setting HDMI preferred device" }
                        } else {
                            logger.info { "successfully selected HDMI for audio output" }
                        }
                    } catch (exception: CancellationException) {
                        logger.debug { "setting HDMI preferred device cancelled" }
                        throw exception
                    } catch (e: Exception) {
                        logger.error(e) { "error setting HDMI preferred device" }
                    }
                }
            }
            ?: logger.info { "still no audio routing, skip" }
    }

    private suspend fun waitForAudioRouting() {
        var trialsCount = 0L
        while (audioRouting.get() == null && trialsCount < 5) {
            delay(1)
            trialsCount++
            logger.debug { "wait till get audio routing" }
        }
    }

    private fun getHdmiDevice(): AudioDeviceInfo? {
        val audioOutputDevices = audioManager.getDevices(GET_DEVICES_OUTPUTS)
        logger.verbose {
            "audio output devices: ${audioOutputDevices.joinToString { deviceTypeToString(it.type) }}"
        }
        return audioOutputDevices.find { it.type == TYPE_HDMI }
    }
}

private fun deviceTypeToString(type: Int): String {
    return when (type) {
        AudioDeviceInfo.TYPE_UNKNOWN -> "TYPE_UNKNOWN"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "TYPE_BUILTIN_EARPIECE"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "TYPE_BUILTIN_SPEAKER"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "TYPE_WIRED_HEADSET"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "TYPE_WIRED_HEADPHONES"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "TYPE_LINE_ANALOG"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "TYPE_LINE_DIGITAL"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "TYPE_BLUETOOTH_SCO"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "TYPE_BLUETOOTH_A2DP"
        AudioDeviceInfo.TYPE_HDMI -> "TYPE_HDMI"
        AudioDeviceInfo.TYPE_HDMI_ARC -> "TYPE_HDMI_ARC"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "TYPE_USB_DEVICE"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "TYPE_USB_ACCESSORY"
        AudioDeviceInfo.TYPE_DOCK -> "TYPE_DOCK"
        AudioDeviceInfo.TYPE_FM -> "TYPE_FM"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "TYPE_BUILTIN_MIC"
        AudioDeviceInfo.TYPE_FM_TUNER -> "TYPE_FM_TUNER"
        AudioDeviceInfo.TYPE_TV_TUNER -> "TYPE_TV_TUNER"
        AudioDeviceInfo.TYPE_TELEPHONY -> "TYPE_TELEPHONY"
        AudioDeviceInfo.TYPE_AUX_LINE -> "TYPE_AUX_LINE"
        AudioDeviceInfo.TYPE_IP -> "TYPE_IP"
        AudioDeviceInfo.TYPE_BUS -> "TYPE_BUS"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "TYPE_USB_HEADSET"
        else -> "TYPE_UNKNOWN"
    }
}
