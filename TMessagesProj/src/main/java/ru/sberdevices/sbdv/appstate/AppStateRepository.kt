package ru.sberdevices.sbdv.appstate

import android.content.Context
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import ru.sberdevices.sbdv.SbdvServiceLocator
import ru.sberdevices.sbdv.model.Contact
import ru.sberdevices.services.appstate.AppStateManagerFactory
import ru.sberdevices.services.appstate.AppStateProvider
import ru.sberdevices.textprocessing.transliteration.BgnLikeTransliterator
import ru.sberdevices.textprocessing.transliteration.Transliterator
import ru.sberdevices.common.coroutines.CoroutineDispatchers as PublicCoroutineDispatchers

private const val TAG = "AppStateRepository"

@MainThread
class AppStateRepository(context: Context) {

    private val stateManager = AppStateManagerFactory.createRequestManager(context, PublicCoroutineDispatchers.Default)

    private val transliterator = BgnLikeTransliterator()

    private val config = SbdvServiceLocator.config

    private val stateProvider = object : AppStateProvider {
        @AnyThread
        override fun getState(): String {
            val (screenState, callState, isAuthenticated) = runBlocking(Dispatchers.Main) {
                Triple(
                    screenStateProvider?.getState(),
                    callStateProvider?.getState(),
                    authStateProvider?.isAuthenticated()
                )
            }

            val json = screenState?.contacts
                ?.toJson(transliterator.takeIf { config.transliterationEnabled })
                ?: JSONObject()

            with(callState ?: CallState()) {
                json.put("cameraEnabled", cameraEnabled)
                    .put("microphoneEnabled", microphoneEnabled)
                    .put("callState", state.toJson())
            }

            json.put("isAuthenticated", isAuthenticated ?: false)

            return json.toString()
        }
    }

    private var screenStateProvider: ScreenStateProvider? = null
    private var callStateProvider: CallStateProvider? = null
    private var authStateProvider: AuthStateProvider? = null

    init {
        stateManager.setProvider(stateProvider)
    }

    fun getCallState(): CallState {
        return callStateProvider?.getState() ?: CallState()
    }

    fun isAuthenticated(): Boolean {
        return authStateProvider?.isAuthenticated() == true
    }

    fun setScreenStateProvider(provider: ScreenStateProvider?) {
        Log.v(TAG, "setScreenStateProvider($provider)")
        screenStateProvider = provider
    }

    fun setCallStateProvider(provider: CallStateProvider?) {
        Log.v(TAG, "setCallStateProvider($provider)")
        callStateProvider = provider
    }

    fun setAuthStateProvider(provider: AuthStateProvider?) {
        Log.v(TAG, "setAuthStateProvider($provider)")
        authStateProvider = provider
    }

    @MainThread
    interface ScreenStateProvider {
        fun getState(): ScreenState?
    }

    @MainThread
    interface CallStateProvider {
        fun getState(): CallState
    }

    @MainThread
    interface AuthStateProvider {
        fun isAuthenticated(): Boolean
    }

    companion object {
        @JvmStatic
        fun isStateActive() = SbdvServiceLocator.getAppStateRepository().getCallState().state == CallState.State.ACTIVE
    }
}

data class ScreenState(val contacts: List<Contact>?)

data class CallState(
    val cameraEnabled: Boolean = false,
    val microphoneEnabled: Boolean = false,
    val state: State = State.NONE,
) {

    enum class State {
        NONE,
        RINGING,
        DIALING,
        ACTIVE,
        RATING
    }
}

private fun CallState.State.toJson(): String {
    return when (this) {
        CallState.State.NONE -> "none"
        CallState.State.RINGING -> "ringing"
        CallState.State.DIALING -> "dialing"
        CallState.State.ACTIVE -> "active"
        CallState.State.RATING -> "rating"
    }
}

private fun List<Contact>.toJson(transliterator: Transliterator?): JSONObject {
    val ignoredWords = JSONArray()
        .put("позвони")
        .put("набери")

    val itemSelector = JSONObject()
        .put("ignored_words", ignoredWords)

    val root = JSONObject()
        .put("item_selector", itemSelector)

    val array = JSONArray()
    for ((index, contact) in this.withIndex()) {
        val preparedFirstName = contact.firstName?.let { transliterator?.transliterate(it) ?: it }
        val preparedLastName = contact.lastName?.let { transliterator?.transliterate(it) ?: it }

        val contactJson = JSONObject()
            .put("number", index + 1)
            .put("id", contact.id.toString())
            .put("first_name", preparedFirstName)
            .put("last_name", preparedLastName)
            .put("visible", true)

        val builder = StringBuilder()
        if (!preparedFirstName.isNullOrEmpty()) builder.append(preparedFirstName)
        if (!preparedFirstName.isNullOrEmpty() && !preparedLastName.isNullOrEmpty()) builder.append(" ")
        if (!preparedLastName.isNullOrEmpty()) builder.append(preparedLastName)
        contactJson.put("title", builder.toString())

        array.put(contactJson)
    }
    itemSelector.put("items", array)

    return root
}
