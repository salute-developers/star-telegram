package ru.sberdevices.sbdv.viewmodel

import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.telegram.messenger.voip.VoIPService
import org.telegram.tgnet.TLRPC
import ru.sberdevices.sbdv.config.Config
import ru.sberdevices.sbdv.model.CallDirection
import ru.sberdevices.sbdv.model.CallEvent
import ru.sberdevices.sbdv.util.getAvatarBitmap
import ru.sberdevices.sbdv.util.getFullName
import ru.sberdevices.sbdv.util.observeOnce
import ru.sberdevices.sbdv.util.toSingleEvent
import ru.sberdevices.services.spotter.state.SpotterStateRepository
import ru.sberdevices.services.spotter.state.entities.SpotterState

private const val TAG = "VoIPViewModel"

private const val FETCH_AVATAR_SIZE_PX = 64
private const val START_SMARTFOCUS_ON_START_CALL = true

/**
 * VoIPViewModel - provide public methods for handle view (VoipFragment) events
 */
class VoIPViewModel(
    private val voipModel: VoIPModel,
    private val spotterRepo: SpotterStateRepository,
    private val config: Config
) : ViewModel() {

    private val _playbackSyncLiveEvent = MutableLiveData<Boolean>()
    val playbackSyncLiveEvent: LiveData<Boolean> = _playbackSyncLiveEvent.toSingleEvent()

    private var _isSmartFocusEnabled = MutableLiveData<Boolean>(START_SMARTFOCUS_ON_START_CALL)
    val isSmartFocusEnabled: LiveData<Boolean> = _isSmartFocusEnabled

    val spotterState: LiveData<SpotterState> = spotterRepo.spotterStateFlow.asLiveData()

    private val callEventObserver = Observer<CallEvent> { callEvent ->
        Log.d(TAG, "On new call event ${callEvent.javaClass.name}")
        when (callEvent) {
            is CallEvent.CallStarted -> {
                onStartCall(callEvent.callId, callEvent.peer, callEvent.direction)
            }
        }
    }

    init {
        Log.d(TAG, "<init> with $config @${hashCode()}")

        voipModel.callEvent.observeForever(callEventObserver)
    }

    @MainThread
    private fun onStartCall(callId: String, peer: TLRPC.User, direction: CallDirection) {
        Log.d(TAG, "onStartCall(callId=$callId, peer=${peer.id}, direction=$direction")
    }

    @MainThread
    fun onToggleSmartFocus() {
        val newValue = !(_isSmartFocusEnabled.value ?: START_SMARTFOCUS_ON_START_CALL)
        Log.d(TAG, "onToggleSmartFocus: $newValue")
        _isSmartFocusEnabled.value = newValue
    }

    @MainThread
    fun onClickCommonViewing() {
        Log.d(TAG, "onClickCommonViewing()" + hashCode())
    }

    @MainThread
    fun onToggleSpotter() {
        // We can not just use spotterRepo.setSpotterActive() because of implementation details of SpotterStateServiceImpl
        VoIPService.getSharedInstance()?.onToggleSpotter(spotterState.value == SpotterState.INACTIVE)
    }

    override fun onCleared() {
        Log.d(TAG, "onCleared()@" + hashCode())
        voipModel.callEvent.removeObserver(callEventObserver)
        spotterRepo.dispose()
        super.onCleared()
    }
}
