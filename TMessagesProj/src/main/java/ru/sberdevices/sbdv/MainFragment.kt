package ru.sberdevices.sbdv

import android.graphics.Color
import android.os.Bundle
import android.telephony.PhoneNumberUtils
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.appcompat.widget.AppCompatToggleButton
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.voip.VideoCapturerDevice
import org.telegram.ui.ActionBar.AlertDialog
import ru.sberdevices.sbdv.appstate.ScreenState
import ru.sberdevices.sbdv.calls.RecentCallsFragment
import ru.sberdevices.sbdv.config.LocalConfiguration
import ru.sberdevices.sbdv.contacts.ContactsFragment
import ru.sberdevices.sbdv.model.AppEvent
import ru.sberdevices.sbdv.util.dimBehind
import ru.sberdevices.sbdv.view.AvatarView

private const val TAG = "MainFragment"
private const val AVATAR_CLICK_FOR_DEV_MODE_COUNT = 10

@Suppress("InflateParams")
class MainFragment : Fragment() {

    private val userMenuLayout by lazy { LayoutInflater.from(context).inflate(R.layout.sbdv_user_menu_layout, null) }
    private val devMenuLayout by lazy { LayoutInflater.from(context).inflate(R.layout.sbdv_dev_menu_layout, null) }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val localConfig by lazy { SbdvServiceLocator.getLocalConfigSharedInstance() }

    private val analyticsCollector = SbdvServiceLocator.getAnalyticsSdkSharedInstance()

    private lateinit var recentCallsView: View
    private lateinit var contactsView: View
    private lateinit var contactsButton: View
    private lateinit var recentCallsButton: View
    private lateinit var menuButton: View

    private var userMenuPopupWindow: PopupWindow? = null
    private var devMenuPopupWindow: PopupWindow? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Log.d(TAG, "onCreateView()")
        return inflater.inflate(R.layout.sbdv_fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        contactsView = view.findViewById(R.id.contactsView)

        recentCallsView = view.findViewById(R.id.recentCallsView)
        recentCallsButton = view.findViewById(R.id.recentCallsButton)
        recentCallsButton.setOnClickListener { selectTab(Tab.RECENT_CALLS) }

        menuButton = view.findViewById(R.id.settingsButton)
        menuButton.setOnClickListener { showMenu() }

        contactsButton = view.findViewById(R.id.contactsButton)
        contactsButton.setOnClickListener { selectTab(Tab.CONTACTS) }

        recentCallsButton.nextFocusLeftId = R.id.recentCallsButton
        recentCallsButton.nextFocusUpId = R.id.recentCallsButton
        recentCallsButton.nextFocusRightId = R.id.contactsButton
        contactsButton.nextFocusUpId = R.id.contactsButton
        contactsButton.nextFocusLeftId = R.id.recentCallsButton
        menuButton.nextFocusUpId = R.id.settingsButton
        menuButton.nextFocusRightId = R.id.settingsButton
        selectTab(Tab.RECENT_CALLS, byUserClick = false)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy()")
        scope.cancel()
    }

    fun getScreenState(): ScreenState? {
        Log.v(TAG, "getScreenState(). Fragment is resumed: $isResumed")
        return if (isResumed) {
            if (contactsView.visibility == View.VISIBLE) {
                val fragment = childFragmentManager.findFragmentById(R.id.contactsView) as ContactsFragment?
                fragment?.getScreenState()
            } else {
                val fragment = childFragmentManager.findFragmentById(R.id.recentCallsView) as RecentCallsFragment?
                fragment?.getScreenState()
            }
        } else {
            null
        }
    }

    private fun showMenu() {
        Log.d(TAG, "showMenu()")
        val marginEnd = requireContext().resources.getDimensionPixelSize(R.dimen.sbdv_logout_popup_margin_end)
        val marginTop = requireContext().resources.getDimensionPixelSize(R.dimen.sbdv_logout_popup_margin_top)

        val user = UserConfig.getInstance(UserConfig.selectedAccount).currentUser ?: return
        val avatarView = userMenuLayout.findViewById<AvatarView>(R.id.userMenuAvatarView)
        avatarView.run {
            setUser(user)
        }
        userMenuLayout.findViewById<TextView>(R.id.userFullNameTextView).run {
            val fullName = "${user.first_name ?: ""} ${user.last_name ?: ""}"
            text = fullName
        }
        val userPhoneTextView = userMenuLayout.findViewById<TextView>(R.id.userPhoneTextView)
        userPhoneTextView.run {
            val phoneNumber = user.phone
            if (phoneNumber != null && phoneNumber.isNotBlank()) {
                val formattedNumber = "+${PhoneNumberUtils.formatNumber(phoneNumber, "RU")}"
                text = formattedNumber
                isVisible = true
            } else {
                isVisible = false
            }
        }
        userMenuLayout.findViewById<TextView>(R.id.usernameTextView).run {
            val username = user.username
            if (username != null && username.isNotBlank()) {
                val usernameWithAt = "@${username}"
                text = usernameWithAt
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }

        userMenuLayout.findViewById<TextView>(R.id.versionTextView).run {
            val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            text = context.getString(R.string.sbdv_version, version)
        }

        userMenuLayout.findViewById<View>(R.id.userMenuLogoutButtonView).apply {
            setOnClickListener { onClickLogout() }
            requestFocus()
        }

        userMenuPopupWindow = PopupWindow(
            userMenuLayout,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            showAsDropDown(menuButton, -marginEnd, -marginTop)
            setOnDismissListener {
                Log.d(TAG, "user menu dismissed")
                userMenuPopupWindow = null
            }
            dimBehind(0.6f)
        }

        scope.launch {
            localConfig.localConfigStateFlow.collect { localConfiguration ->
                if (localConfiguration.developerModeEnabled) {
                    avatarView.setOnClickListener { showDevOptionsMenu(localConfiguration) }
                } else {
                    var avatarClicksCount = 0

                    fun checkDevClick() {
                        if (avatarClicksCount == AVATAR_CLICK_FOR_DEV_MODE_COUNT) {
                            Toast.makeText(context, "Developer options enabled", Toast.LENGTH_SHORT).show()
                            localConfig.onEnableDeveloperMode(true)
                            avatarView.setOnClickListener { showDevOptionsMenu(localConfiguration) }
                            avatarClicksCount = 0
                        }
                        if (avatarClicksCount > AVATAR_CLICK_FOR_DEV_MODE_COUNT) {
                            avatarClicksCount = 0
                        }
                    }

                    avatarView.setOnClickListener {
                        avatarClicksCount++
                        if (avatarClicksCount == AVATAR_CLICK_FOR_DEV_MODE_COUNT) checkDevClick()
                    }
                }
            }
        }
    }

    @MainThread
    private fun showDevOptionsMenu(configuration: LocalConfiguration) {
        Log.d(TAG, "showDevOptionsMenu()")
        val mockCallsToggleButton = devMenuLayout.findViewById<AppCompatToggleButton>(R.id.mockCallsToggleButton)
        mockCallsToggleButton.isChecked = localConfig.localConfigStateFlow.value.mockCallsOn
        mockCallsToggleButton.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            localConfig.onEnableMockCalls(isChecked)
        }

        devMenuLayout.findViewById<Spinner>(R.id.sbdv_crop_source_resolution).apply {
            adapter = ArrayAdapter(
                context,
                R.layout.sbdv_spinner_item,
                VideoCapturerDevice.CROP_SOURCE_RESOLUTIONS
            ).apply {
                setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item)
            }

            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    localConfig.setCropSourceResolution(VideoCapturerDevice.CROP_SOURCE_RESOLUTIONS[position])
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    localConfig.setCropSourceResolution(Size(0, 0))
                }
            }

            val selected: Int = if (configuration.cropSourceResolution != null) {
                val size = Size(configuration.cropSourceResolution.width, configuration.cropSourceResolution.height)
                findSelectedResolutionIndex(size)
            } else {
                0
            }
            setSelection(selected)
        }

        devMenuLayout.findViewById<AppCompatToggleButton>(R.id.sbdv_dewarp_by_crop).apply {
            isChecked = localConfig.localConfigStateFlow.value.dewarpByCropEnabled
            setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                localConfig.setDewarpByCrop(isChecked)
            }
        }

        devMenuLayout.findViewById<EditText>(R.id.sbdv_capture_fps).apply {
            setText(localConfig.localConfigStateFlow.value.captureFps.toString())
            setOnEditorActionListener { view, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    val fps = view.text.toString().toInt()
                    localConfig.setCropFps(fps)
                    true
                } else {
                    false
                }
            }
        }

        devMenuLayout.findViewById<AppCompatToggleButton>(R.id.sbdv_avoid_h265_encoder).apply {
            isChecked = localConfig.localConfigStateFlow.value.avoidH265Encoder
            setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                localConfig.setAvoidH265Encoder(isChecked)
            }
        }

        devMenuPopupWindow = PopupWindow(
            devMenuLayout,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            showAsDropDown(menuButton, -100, -100)
            setOnDismissListener {
                Log.d(TAG, "dev menu dismissed")
                devMenuPopupWindow = null
            }
            dimBehind(0.6f)
        }
    }

    private fun findSelectedResolutionIndex(size: Size): Int {
        var foundIdx = 0
        for (i in VideoCapturerDevice.CROP_SOURCE_RESOLUTIONS.indices) {
            if (VideoCapturerDevice.CROP_SOURCE_RESOLUTIONS[i] == size) {
                foundIdx = i
                break
            }
        }
        return foundIdx
    }

    @MainThread
    private fun onClickLogout() {
        Log.d(TAG, "onClickLogout()")
        val dialogView: View = FrameLayout.inflate(context, R.layout.sbdv_alert_dialog_big, null)
        val builder = AlertDialog.Builder(context).apply {
            setView(dialogView)
        }
        val dialog = builder.create()

        val dialogMargin = requireContext().resources.getDimensionPixelSize(R.dimen.sbdv_dialog_content_vertical_margin)
        dialog.window?.let { window ->
            window.setGravity(Gravity.END)
            window.attributes.x = dialogMargin
        }
        dialog.setBackgroundColor(Color.TRANSPARENT)
        dialog.show()

        val backgroundView = dialogView.findViewById<View>(R.id.backgroundConstraintLayout)
        if (SbdvServiceLocator.config.newBackgroundEnabled) {
            backgroundView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.sbdv_menu_background))
        } else {
            backgroundView.setBackgroundResource(R.drawable.sbdv_dialog_background)
        }

        val title = dialogView.findViewById<TextView>(R.id.sdbv_dialog_title)
        title.text = LocaleController.getString("LogOut", R.string.LogOut)

        val message = dialogView.findViewById<TextView>(R.id.sdbv_dialog_message)
        message.text = AndroidUtilities.replaceTags(
            LocaleController.formatString("AreYouSureLogout", R.string.AreYouSureLogout)
        )

        val logOffButton = dialogView.findViewById<Button>(R.id.sdbv_dialog_negative)
        logOffButton.text = LocaleController.getString("LogOff", R.string.LogOff)
        logOffButton.setOnClickListener {
            SbdvServiceLocator.getAnalyticsSdkSharedInstance().onAppEvent(AppEvent.LOGOUT)
            MessagesController.getInstance(UserConfig.selectedAccount).performLogout(1)
            dialog.dismiss()
            clearPopupWindows()
        }

        val cancelButton = dialogView.findViewById<Button>(R.id.sdbv_dialog_positive)
        cancelButton.text = LocaleController.getString("StayHere", R.string.StayHere)
        cancelButton.requestFocus()
        cancelButton.setOnClickListener { dialog.dismiss() }
    }

    private fun clearPopupWindows() {
        Log.d(TAG, "clearPopupWindows()")
        userMenuPopupWindow?.dismiss()
        userMenuPopupWindow = null

        devMenuPopupWindow?.dismiss()
        devMenuPopupWindow = null
    }

    private fun selectTab(tab: Tab, byUserClick: Boolean = true) {
        recentCallsButton.isSelected = tab == Tab.RECENT_CALLS
        contactsButton.isSelected = tab == Tab.CONTACTS
        recentCallsView.isInvisible = tab != Tab.RECENT_CALLS
        contactsView.isInvisible = tab != Tab.CONTACTS

        if (byUserClick) {
            val appEvent = when (tab) {
                Tab.RECENT_CALLS -> AppEvent.CLICK_RECENT_LIST
                Tab.CONTACTS -> AppEvent.CLICK_CONTACTS_LIST
            }
            analyticsCollector.onAppEvent(appEvent)
        }
    }

    private enum class Tab {
        RECENT_CALLS, CONTACTS
    }
}
