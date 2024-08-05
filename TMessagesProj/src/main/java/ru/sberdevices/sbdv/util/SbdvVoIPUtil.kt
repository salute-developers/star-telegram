package ru.sberdevices.sbdv.util

import org.jetbrains.annotations.NotNull
import org.telegram.messenger.voip.VoIPService

/**
 * @author Anatoliy Gordienko on 19.05.2022
 */
object SbdvVoIPUtil {

    /**
     * Получаем читаемое состояние для [state]. Если передан null, то получаем описание
     * текущего callState у [VoIPService.getSharedInstance]
     */
    @JvmStatic
    @NotNull
    fun getCurrentState(state: Int? = null): String {
        if (state == null && VoIPService.getSharedInstance() == null) return "STATE_VOIP_SERVICE_NULL"
        return when (val currentState = state ?: VoIPService.getSharedInstance().callState) {
            VoIPService.STATE_BUSY -> "STATE_BUSY"
            VoIPService.STATE_ENDED -> "STATE_ENDED"
            VoIPService.STATE_ESTABLISHED -> "STATE_ESTABLISHED"
            VoIPService.STATE_EXCHANGING_KEYS -> "STATE_EXCHANGING_KEYS"
            VoIPService.STATE_FAILED -> "STATE_FAILED"
            VoIPService.STATE_HANGING_UP -> "STATE_HANGING_UP"
            VoIPService.STATE_RECONNECTING -> "STATE_RECONNECTING"
            VoIPService.STATE_REQUESTING -> "STATE_REQUESTING"
            VoIPService.STATE_RINGING -> "STATE_RINGING"
            VoIPService.STATE_WAITING -> "STATE_WAITING"
            VoIPService.STATE_WAITING_INCOMING -> "STATE_WAITING_INCOMING"
            VoIPService.STATE_WAIT_INIT -> "STATE_WAIT_INIT"
            VoIPService.STATE_WAIT_INIT_ACK -> "STATE_WAIT_INIT_ACK"
            else -> "STATE_UNKNOWN_$currentState"
        }
    }

    @JvmStatic
    @NotNull
    fun getCurrentState(): String {
        return getCurrentState(null)
    }

    @JvmStatic
    fun isStateWaitingIncoming(): Boolean {
        val service = VoIPService.getSharedInstance()
        return if (service != null) service.callState == VoIPService.STATE_WAITING_INCOMING else true
    }
}
