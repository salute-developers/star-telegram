package ru.sberdevices.sbdv.model

import org.telegram.messenger.voip.VoIPService

/**
 * Для более понятного логирования изменений состояний звонка в [VoIPService]
 * @author Ирина Карпенко on 11.11.2021
 */
enum class VoIPState(val id: Int) {
    STATE_WAIT_INIT(VoIPService.STATE_WAIT_INIT),
    STATE_WAIT_INIT_ACK(VoIPService.STATE_WAIT_INIT_ACK),
    STATE_ESTABLISHED(VoIPService.STATE_ESTABLISHED),
    STATE_FAILED(VoIPService.STATE_FAILED),
    STATE_RECONNECTING(VoIPService.STATE_RECONNECTING),
    STATE_CREATING(VoIPService.STATE_CREATING),
    STATE_ENDED(VoIPService.STATE_ENDED),
    STATE_HANGING_UP(VoIPService.STATE_HANGING_UP),
    STATE_EXCHANGING_KEYS(VoIPService.STATE_EXCHANGING_KEYS),
    STATE_WAITING(VoIPService.STATE_WAITING),
    STATE_REQUESTING(VoIPService.STATE_REQUESTING),
    STATE_WAITING_INCOMING(VoIPService.STATE_WAITING_INCOMING),
    STATE_RINGING(VoIPService.STATE_RINGING),
    STATE_BUSY(VoIPService.STATE_BUSY);

    companion object {
        @JvmStatic
        fun get(id: Int): VoIPState? {
            return values().find { it.id == id }
        }
    }
}