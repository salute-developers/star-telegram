package ru.sberdevices.sbdv

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Observer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.sbdv.SbdvServiceLocator.getAppStateRepository
import ru.sberdevices.sbdv.SbdvServiceLocator.getVoIPModelSharedInstance
import ru.sberdevices.sbdv.appstate.AppStateRepository
import ru.sberdevices.sbdv.appstate.CallState
import ru.sberdevices.sbdv.appstate.ScreenState
import ru.sberdevices.sbdv.model.AppEvent
import ru.sberdevices.sbdv.model.CallEvent
import ru.sberdevices.sbdv.search.ContactSearchFragment

private const val TAG = "SbdvMainContainer"

private const val FRAGMENT_TAG_MAIN = "main"
private const val INTENT_ACTION_MAKE_CALL = "ru.sberdevices.telegramcalls.action.MAKE_CALL"
private const val INTENT_EXTRA_CALLEE_ID = "callee_id"
private const val CALLEE_ID_KEY = "callee_id"
private const val LOGIN_INTENT_COMPONENT_CLASS_NAME = "ru.sberdevices.telegramcalls.CallVendorLoginActivity"
private const val MAKE_CALL_INTENT_COMPONENT_CLASS_NAME = "ru.sberdevices.telegramcalls.MakeCallActivity"

class SbdvMainContainer(context: Context) : FrameLayout(context) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + SbdvServiceLocator.getCoroutineDispatchersSharedInstance().ui)
    private val logger by Logger.lazy<SbdvMainContainer>()
    private val fragmentManager = (context as AppCompatActivity).supportFragmentManager
    private val stateAppRepository = SbdvServiceLocator.getAppStateRepository()

    private val voipModel = SbdvServiceLocator.getVoIPModelSharedInstance()

    private val stateProvider = object : AppStateRepository.ScreenStateProvider {
        override fun getState(): ScreenState? {
            val searchFragment = fragmentManager.findFragmentByTag(ContactSearchFragment.NAVIGATION_TAG)
            val mainFragment = fragmentManager.findFragmentByTag(FRAGMENT_TAG_MAIN)
            return when {
                searchFragment != null -> (searchFragment as ContactSearchFragment).getScreenState()
                mainFragment != null -> (mainFragment as MainFragment).getScreenState()
                else -> null
            }
        }
    }

    private val analyticsCollector = SbdvServiceLocator.getAnalyticsSdkSharedInstance()

    @Volatile
    private var started: Boolean = false

    @Volatile
    private var makingCall: Boolean = false

    @Volatile
    var hasEverBeenVisibleWhileStarted: Boolean = false

    private val config = SbdvServiceLocator.config

    private val mainFragmentView: FragmentContainerView
    private val backgroundImageView: ImageView

    private val authorizationFeature = SbdvServiceLocator.getAuthorizationFeatureSharedInstance()

    init {
        LayoutInflater.from(context).inflate(R.layout.sbdv_container, this, true)
        mainFragmentView = findViewById(R.id.mainFragmentView)
        backgroundImageView = findViewById(R.id.backgroundImageView)
        pause() // чтобы не отображать контейнер по дефолту, когда он не нужен
    }

    fun onStart() {
        Log.d(TAG, "onStart()")
        subscribeToConfig()
        stateAppRepository.setScreenStateProvider(stateProvider)

        if (ContactSearchFragment.hasContactsExtra(context)) {
            handleIntent((context as AppCompatActivity).intent)
        } else {
            val searchFragment = fragmentManager.findFragmentByTag(ContactSearchFragment.NAVIGATION_TAG)
            if (searchFragment != null) fragmentManager.popBackStack()
        }
        started = true
        hasEverBeenVisibleWhileStarted = visibility == View.VISIBLE
    }

    fun resume() {
        Log.d(TAG, "resume()")
        if (started && !makingCall) {
            val callState = getAppStateRepository().getCallState().state
            authorizationFeature.refreshProfiles()
            val authorized = UserConfig.getInstance(UserConfig.selectedAccount).isClientActivated
            Log.d(TAG, "Call state: ${callState.name}, authorized: $authorized")
            if ((callState == CallState.State.NONE || callState == CallState.State.RINGING) && authorized) {
                val targetVisibility = View.VISIBLE
                if (targetVisibility != visibility) {
                    Log.d(TAG, "container is started, resume")
                    analyticsCollector.onAppEvent(AppEvent.OPEN_MAIN_SCREEN)
                    hasEverBeenVisibleWhileStarted = true
                    visibility = targetVisibility
                } else {
                    Log.d(TAG, "container is started and already resumed, skip")
                }
            }
        }
    }

    fun pause() {
        Log.d(TAG, "pause()")
        authorizationFeature.refreshProfiles()
        val targetVisibility = View.GONE
        if (targetVisibility != visibility) {
            visibility = targetVisibility
        }
    }

    private fun subscribeToConfig() {
        logger.debug { "observe config" }
        config.newBackgroundEnabledFlow
            .onEach { newGradientBackgroundEnabled ->
                logger.debug { "new gradient background enabled: $newGradientBackgroundEnabled" }
                if (newGradientBackgroundEnabled) {
                    backgroundImageView.setBackgroundResource(R.drawable.sbdv_new_background)
                } else {
                    backgroundImageView.setBackgroundResource(R.drawable.sbdv_background)
                }
            }
            .launchIn(coroutineScope)
        combine(
            config.integratedWithSingleCallsPlaceFlow,
            authorizationFeature.authorized
        ) { integrated, authorized -> integrated to authorized }
            .distinctUntilChanged()
            .onEach { (integrated, authorized) ->
                val callState = stateAppRepository.getCallState().state
                val isDisplayingVoip = (callState != CallState.State.NONE && callState != CallState.State.RINGING)
                logger.debug {
                    "integrated with single calls place: $integrated, authorized: $authorized, " +
                        "displaying voip: $isDisplayingVoip"
                }
                if (!integrated && authorized) {
                    stateAppRepository.setScreenStateProvider(stateProvider)
                    val searchOpen = fragmentManager.findFragmentByTag(ContactSearchFragment.NAVIGATION_TAG) != null
                    val mainOpen = fragmentManager.findFragmentByTag(FRAGMENT_TAG_MAIN) != null
                    visibility = if (isDisplayingVoip) View.GONE else View.VISIBLE
                    if (!searchOpen && !mainOpen && !isDisplayingVoip) {
                        if (ContactSearchFragment.hasContactsExtra(context)) {
                            showContactSearchFragment()
                        } else {
                            showMainFragment()
                        }
                    }
                } else {
                    stateAppRepository.setScreenStateProvider(null)
                }
            }
            .onCompletion { logger.debug { "observe config completed" } }
            .launchIn(coroutineScope)
    }

    fun onStop() {
        Log.d(TAG, "onStop()")
        started = false
        hasEverBeenVisibleWhileStarted = false
        coroutineScope.coroutineContext.cancelChildren()
        stateAppRepository.setScreenStateProvider(null)
    }

    fun onBackPressed(): Boolean {
        val searchFragment = fragmentManager.findFragmentByTag(ContactSearchFragment.NAVIGATION_TAG) as? ContactSearchFragment
        if (searchFragment != null) {
            searchFragment.onBackPressed()
            return true
        }

        return false
    }

    fun onNewIntent(intent: Intent) {
        Log.d(TAG, "onNewIntent")

        (context as AppCompatActivity).intent = intent

        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        Log.d(TAG, "handleIntent")

        if (config.integratedWithSingleCallsPlace && !intent.containsMakeCallAction()) {
            handleCallVendorIntent(intent)
        } else {
            if (ContactSearchFragment.hasContactsExtra(context)) {
                val searchFragment = fragmentManager.findFragmentByTag(ContactSearchFragment.NAVIGATION_TAG)
                if (searchFragment == null) {
                    showContactSearchFragment()
                } else {
                    (searchFragment as ContactSearchFragment).onNewIntent(intent)
                }
            } else if (intent.containsMakeCallAction()) {
                Log.d(TAG, "MAKE_CALL action received")
                try {
                    val calleeId = intent.getStringExtra(INTENT_EXTRA_CALLEE_ID)!!.toLong()
                    callUser(calleeId)
                } catch (e: Exception) {
                    when (e) {
                        is NullPointerException, is NumberFormatException -> {
                            Log.e(TAG, "Cannot parse calleeId: ${e.localizedMessage}")
                        }
                        else -> throw e
                    }
                }
                (context as AppCompatActivity).intent.removeExtra(INTENT_EXTRA_CALLEE_ID)
            } else {
                Log.d(TAG, "No contacts extra or make call action with callee id")
            }
        }
    }

    private fun showContactSearchFragment() {
        logger.debug { "showContactSearchFragment()" }
        fragmentManager.beginTransaction()
            .replace(
                R.id.mainFragmentView,
                ContactSearchFragment::class.java,
                null,
                ContactSearchFragment.NAVIGATION_TAG
            )
            .addToBackStack(ContactSearchFragment.NAVIGATION_TAG)
            .commit()

        analyticsCollector.onAppEvent(AppEvent.SHOW_VOICE_SEARCH_RESULT_LIST)
    }

    private fun showMainFragment() {
        logger.debug { "showMainFragment()" }
        fragmentManager.beginTransaction()
            .replace(R.id.mainFragmentView, MainFragment::class.java, null, FRAGMENT_TAG_MAIN)
            .addToBackStack(FRAGMENT_TAG_MAIN)
            .commit()
    }

    private fun handleCallVendorIntent(intent: Intent) {
        when (intent.component?.className) {
            LOGIN_INTENT_COMPONENT_CLASS_NAME -> {
                logger.debug { "received login intent" }
                onLoginRequested()
            }
            MAKE_CALL_INTENT_COMPONENT_CLASS_NAME -> {
                logger.debug { "received make call intent" }
                val calleeId = intent.data?.getQueryParameter(CALLEE_ID_KEY)
                onMakeCallRequested(calleeId)
            }
            else -> {
                logger.debug { "received intent for ${intent.component?.className}" }
            }
        }
    }

    private fun onLoginRequested() {
        logger.debug { "onLoginRequested" }
    }

    private fun onMakeCallRequested(calleeId: String?) {
        logger.debug { "onMakeCallRequested(calleeId=$calleeId)" }
        try {
            if (!calleeId.isNullOrBlank()) {
                val userId = calleeId.toLong()
                val messagesController = MessagesController.getInstance(UserConfig.selectedAccount)
                val user = messagesController.getUser(userId)
                if (user != null) {
                    voipModel.onOutgoingCallUserIntent(
                        activity = context as AppCompatActivity,
                        user = user,
                        userFull = messagesController.getUserFull(user.id)
                    )
                }
            } else {
                logger.warn { "cannot parse callee id [$calleeId]" }
            }
        } catch (exception: NumberFormatException) {
            logger.warn(exception) { "cannot parse callee id [$calleeId]" }
        }
    }

    private fun callUser(userId: Long) {
        Log.d(TAG, "callUser")
        val messagesController = MessagesController.getInstance(UserConfig.selectedAccount)
        val user = messagesController.getUser(userId)

        if (user != null) {
            val needMockCalls = SbdvServiceLocator.getLocalConfigSharedInstance().localConfigStateFlow.value.mockCallsOn
            if (needMockCalls) {
                Toast.makeText(
                    context,
                    "Mock voice call to " + user.first_name.orEmpty() + " " + user.last_name.orEmpty() + " " + user.username.orEmpty(),
                    Toast.LENGTH_LONG
                ).apply {
                    setGravity(Gravity.CENTER, 0, 0)
                    show()
                }
            } else {
                val isSearchFragmentVisible = fragmentManager.findFragmentByTag(ContactSearchFragment.NAVIGATION_TAG) != null
                voipModel.onOutgoingCallUserIntent(
                    activity = (context as AppCompatActivity),
                    userId = userId,
                    afterVoiceSearch = isSearchFragmentVisible,
                    afterVoiceDirectCommand = true
                )
                makingCall = true
                Log.d(TAG, "making call")
                val callEvents = getVoIPModelSharedInstance().callEvent
                val callEventsListener = object : Observer<CallEvent> {
                    override fun onChanged(event: CallEvent) {
                        Log.d(TAG, "call event $event")
                        if (event is CallEvent.EndCall || event is CallEvent.CancelInviting) {
                            makingCall = false
                            Log.d(TAG, "not making call")
                            callEvents.removeObserver(this)
                        }
                    }
                }
                callEvents.observeForever(callEventsListener)
            }
        }
    }

    private fun Intent.containsMakeCallAction(): Boolean = action == INTENT_ACTION_MAKE_CALL && hasExtra(INTENT_EXTRA_CALLEE_ID)
}
