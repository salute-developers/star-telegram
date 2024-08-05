package ru.sberdevices.sbdv.viewmodel

import android.app.Activity
import android.util.ArrayMap
import android.util.Log
import androidx.annotation.AnyThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.voip.VoIPHelper
import org.webrtc.VideoCodecInfo
import ru.sberdevices.sbdv.analytics.AnalyticsCollector
import ru.sberdevices.sbdv.model.CallDirection
import ru.sberdevices.sbdv.model.CallEvent
import ru.sberdevices.sbdv.model.CallTechnicalInfo
import ru.sberdevices.sbdv.util.toSingleEvent
import ru.sberdevices.telegramcalls.vendor.tray.Tray

private inline class PeerId(val value: Long)

private class OutgoingCallIntent(val afterVoiceSearch: Boolean, val afterVoiceDirectCommand: Boolean)

/**
 * VoIPModel - not bounded to view lifecycle, which provided public methods for handle calling states
 */
@AnyThread
class VoIPModel(
    private val analyticsCollector: AnalyticsCollector,
    private val tray: Tray
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun getUser() = MessagesController.getInstance(UserConfig.selectedAccount)
        .getUser(UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId())

    private val _callEvent = MutableLiveData<CallEvent>()
    val callEvent: LiveData<CallEvent> = _callEvent.toSingleEvent()

    private val _callTechnicalInfoLiveData = MutableLiveData<CallTechnicalInfo>()
    val callTechnicalInfoLiveData: LiveData<CallTechnicalInfo>
        get() = _callTechnicalInfoLiveData

    private val outgoingCallIntents = ArrayMap<PeerId, OutgoingCallIntent>()

    private fun handleCallEvent(event: CallEvent) {
        Log.d(TAG, "onCallEvent($event)")
        _callEvent.postValue(event)
        scope.launch {
            analyticsCollector.onCallEvent(event)
        }
    }

    fun onCallInviting(callId: String, peer: TLRPC.User, direction: CallDirection) {
        if (direction == CallDirection.OUT) {
            val outgoingCallIntent: OutgoingCallIntent? = outgoingCallIntents.remove(PeerId(peer.id))
            handleCallEvent(CallEvent.InvitingToCall(callId, direction, peer,
                afterVoiceSearch = outgoingCallIntent?.afterVoiceSearch,
                afterVoiceDirectCommand = outgoingCallIntent?.afterVoiceDirectCommand
            ))
        } else {
            handleCallEvent(CallEvent.InvitingToCall(callId, direction, peer))
        }
    }

    fun onStartCall(callId: String, peer: TLRPC.User, direction: CallDirection, technicalInfo: CallTechnicalInfo?) {
        handleCallEvent(CallEvent.CallStarted(callId, direction, peer, technicalInfo))
    }

    fun onCancelInviting(callId: String, direction: CallDirection, reason: String) {
        handleCallEvent(CallEvent.CancelInviting(callId, direction, reason))
    }

    fun onEndCall(event: CallEvent.EndCall) {
        handleCallEvent(event)
    }

    fun onCallError(callId: String, message: String) {
        handleCallEvent(CallEvent.CallError(callId, message))
    }

    fun onCallRate(callId: String, rating: Int) {
        handleCallEvent(CallEvent.SetCallRating(callId, rating))
    }

    fun onOutgoingCallUserIntent(
        activity: Activity,
        userId: Long,
        afterVoiceSearch: Boolean = false,
        afterVoiceDirectCommand: Boolean = false
    ) {
        Log.d(TAG, "onOutgoingCallUserIntent($activity, $userId, $afterVoiceSearch, $afterVoiceDirectCommand)")

        val messagesController = MessagesController.getInstance(UserConfig.selectedAccount)
        val user = messagesController.getUser(userId)

        if (user != null) {
            val userFull = messagesController.getUserFull(user.id)
            outgoingCallIntents[PeerId(userId)] = OutgoingCallIntent(
                afterVoiceSearch = afterVoiceSearch,
                afterVoiceDirectCommand = afterVoiceDirectCommand
            )
            VoIPHelper.startCall(user, true, userFull != null && userFull.video_calls_available, activity, userFull, AccountInstance.getInstance(0))
        } else {
            Log.e(TAG, "Not found user with id = $userId")
        }
    }

    fun onOutgoingCallUserIntent(
        activity: Activity,
        user: TLRPC.User,
        userFull: TLRPC.UserFull?
    ) {
        outgoingCallIntents[PeerId(user.id)] = OutgoingCallIntent(
            afterVoiceSearch = false,
            afterVoiceDirectCommand = false
        )
        VoIPHelper.startCall(user, true, userFull != null && userFull.video_calls_available, activity, userFull, AccountInstance.getInstance(0))
    }

    fun onCodecsSelected(
        encoder: VideoCodecInfo?,
        decoder: VideoCodecInfo?
    ) {
        Log.d(TAG, "Encoder: " + encoder?.name + "; Decoder: " + decoder?.name + ";")
        val callTechnicalInfo = CallTechnicalInfo(encoder?.name, decoder?.name)
        _callTechnicalInfoLiveData.postValue(callTechnicalInfo)
    }

    fun onVoipServiceCreated() {
        Log.d(TAG, "onVoipServiceCreated()")
        scope.launch { tray.join() }
    }

    fun onVoipServiceDestroyed() {
        Log.d(TAG, "onVoipServiceDestroyed()")
        scope.launch { tray.leave() }
    }

    companion object {
        private val TAG = "VoIPModel"
    }
}