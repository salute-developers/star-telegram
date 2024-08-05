package ru.sberdevices.sbdv

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.annotation.CallSuper
import androidx.fragment.app.Fragment
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import java.util.ArrayList

private const val TAG = "SbdvBaseFragment"

abstract class SbdvBaseFragment : Fragment(), NotificationCenter.NotificationCenterDelegate {
    private val notificationCenter: NotificationCenter by lazy {
        NotificationCenter.getInstance(UserConfig.selectedAccount)
    }

    protected val messagesController: MessagesController by lazy {
        MessagesController.getInstance(UserConfig.selectedAccount)
    }

    @CallSuper
    open fun onMainUserInfoChange() {
        Log.d(TAG, "onMainUserInfoChange()")
    }

    @CallSuper
    open fun onLogout() {
        Log.d(TAG, "onLogout(). Still logged in ${UserConfig.getActivatedAccountsCount()} accounts")

        SbdvServiceLocator.getStarNotificationsInstance().deleteAllNotificationsOnLogout()
    }

    open fun onNewMessages(messages: ArrayList<MessageObject>) {}

    open fun onDeleteMessages() {}

    private val voipModel = SbdvServiceLocator.getVoIPModelSharedInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        notificationCenter.addObserver(this, NotificationCenter.didReceiveNewMessages)
        notificationCenter.addObserver(this, NotificationCenter.messagesDeleted)
        notificationCenter.addObserver(this, NotificationCenter.mainUserInfoChanged)
        notificationCenter.addObserver(this, NotificationCenter.appDidLogout)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        notificationCenter.removeObserver(this, NotificationCenter.didReceiveNewMessages)
        notificationCenter.removeObserver(this, NotificationCenter.messagesDeleted)
        notificationCenter.removeObserver(this, NotificationCenter.mainUserInfoChanged)
        notificationCenter.removeObserver(this, NotificationCenter.appDidLogout)
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        when (id) {
            NotificationCenter.didReceiveNewMessages -> {
                val scheduled = args[2] as Boolean
                if (!scheduled) {
                    val messages = args[1] as ArrayList<MessageObject>
                    onNewMessages(messages)
                }
            }
            NotificationCenter.messagesDeleted -> {
                val scheduled = args[2] as Boolean
                if (!scheduled) {
                    onDeleteMessages()
                }
            }
            NotificationCenter.mainUserInfoChanged -> onMainUserInfoChange()
            NotificationCenter.appDidLogout -> onLogout()
        }
    }

    internal fun onCallToUserClick(userId: Long, fromVoiceSearch: Boolean = false) {
        Log.d(TAG, "onCallToUserClick()")
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
                voipModel.onOutgoingCallUserIntent(
                    activity = requireActivity(),
                    userId = userId,
                    afterVoiceSearch = fromVoiceSearch,
                    afterVoiceDirectCommand = false
                )
            }
        }
    }
}
