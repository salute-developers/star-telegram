package ru.sberdevices.sbdv.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import org.json.JSONObject
import org.telegram.messenger.R
import ru.sberdevices.sbdv.SbdvBaseFragment
import ru.sberdevices.sbdv.appstate.ScreenState
import ru.sberdevices.sbdv.contacts.ContactsAdapter
import ru.sberdevices.sbdv.model.Contact
import ru.sberdevices.sbdv.util.DeviceUtils
import kotlin.math.min

private const val TAG = "ContactSearchFragment"

class ContactSearchFragment : SbdvBaseFragment() {

    private val contactsAdapter = ContactsAdapter(
        { _, _ -> },
        { onCallToUserClick(it, fromVoiceSearch = true) },
        { _, _ -> }
    )

    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var emptyContactsLayout: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Log.d(TAG, "onCreateView")
        return inflater.inflate(R.layout.sbdv_fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recyclerView = view.findViewById<RecyclerView>(R.id.searchRecyclerView).apply {
            layoutManager = this@ContactSearchFragment.layoutManager
            adapter = contactsAdapter
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, state: Int) {
                    if (state == RecyclerView.SCROLL_STATE_IDLE) {
                        contactsAdapter.updateCardNumbers(
                            firstVisiblePosition = this@ContactSearchFragment.layoutManager.findFirstVisibleItemPosition(),
                            lastVisiblePosition = this@ContactSearchFragment.layoutManager.findLastVisibleItemPosition()
                        )
                    } else {
                        contactsAdapter.clearCardNumbers()
                    }
                }
            })
        }

        emptyContactsLayout = view.findViewById(R.id.searchEmptyContactsLayout)

        initBackNavigationControls(view)

        activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")

        updateContacts()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")

        (context as AppCompatActivity).intent.removeExtra(CONTACTS_EXTRA)
    }

    fun onNewIntent(@Suppress("UNUSED_PARAMETER") intent: Intent) {
        Log.d(TAG, "onNewIntent")

        updateContacts()
    }

    fun getScreenState(): ScreenState? {
        Log.v(TAG, "getScreenState(). Fragment is resumed: $isResumed")
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager?
        val firstPosition = layoutManager?.findFirstVisibleItemPosition()
        val lastPosition = layoutManager?.findLastVisibleItemPosition()

        return if (isResumed && firstPosition != null && lastPosition != null && lastPosition != RecyclerView.NO_POSITION) {
            val allContacts = contactsAdapter.getContacts()
            ScreenState(allContacts.subList(Integer.max(firstPosition, 0), min(lastPosition + 1, allContacts.size)))
        } else {
            null
        }
    }

    private fun initBackNavigationControls(view: View) {
        if (DeviceUtils.isHuawei()) {
            view.findViewById<LinearLayout>(R.id.searchHeader).isInvisible = true
            view.requestFocus()
            view.setOnKeyListener(View.OnKeyListener { _, keyCode, _ ->
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    onBackPressed()

                    return@OnKeyListener true
                }
                false
            })
        } else {
            view.findViewById<ImageButton>(R.id.searchUpButton).setOnClickListener {
                onBackPressed()
            }
        }
    }

    fun onBackPressed() {
        val activity = context as? AppCompatActivity
        if (activity != null) {
            activity.intent.removeExtra(CONTACTS_EXTRA)
            with(activity.supportFragmentManager) {
                val backstack = (0 until backStackEntryCount).mapNotNull { entry ->
                    getBackStackEntryAt(entry).name
                }
                Log.d(TAG, "popBackStack(). Backstack entry count: $backStackEntryCount, backstack: $backstack")
                if (backStackEntryCount == 0 || (backStackEntryCount == 1 && getBackStackEntryAt(0).name == NAVIGATION_TAG)) {
                    Log.d(TAG, "finish()")
                    activity.finish()
                } else {
                    Log.d(TAG, "popBackStack()")
                    popBackStack()
                }
            }
        }
    }

    private fun updateContacts() {
        val contacts = getContactsFromBundle()
        if (contacts.isNotEmpty()) {
            showContacts(contacts)
        } else {
            showContactsNotFoundPlaceholder()
        }
    }

    private fun getContactsFromBundle(): List<Contact> {
        val contactsList = mutableListOf<Contact>()

        try {
            val bundle = (context as AppCompatActivity).intent.extras
            if (bundle != null) {
                val contactsJson = bundle.getString(CONTACTS_EXTRA)
                if (!contactsJson.isNullOrEmpty()) {
                    val array = JSONObject(contactsJson)
                        .getJSONObject("item_selector")
                        .getJSONArray("items")
                    Log.d(TAG, "Contacts array size = ${array.length()}")
                    for (i in 0 until array.length()) {
                        val contactJson = array.getJSONObject(i)
                        val id = contactJson.optLong("id")
                        messagesController.users[id]?.let { user ->
                            contactsList.add(
                                Contact(
                                    id = id,
                                    firstName = user.first_name,
                                    lastName = user.last_name,
                                    user = user,
                                    displayedNumber = null
                                )
                            )
                        }
                    }
                }
            }
        } catch (thr: Throwable) {
            Log.e(TAG, "Can't parse item_selector", thr)
        }

        Log.d(TAG, "Contacts size = ${contactsList.size}")
        return contactsList
    }

    private fun showContacts(contacts: List<Contact>) {
        Log.d(TAG, "showContacts(); ${contacts.size} contacts")

        emptyContactsLayout.isVisible = false
        recyclerView.isVisible = true
        contactsAdapter.setContacts(contacts, false)

        val currentScrollState = recyclerView.scrollState
        if (currentScrollState == RecyclerView.SCROLL_STATE_IDLE || currentScrollState == RecyclerView.SCROLL_STATE_SETTLING) {
            val firstPosition = layoutManager.findFirstVisibleItemPosition()
            val lastPosition = layoutManager.findLastVisibleItemPosition()

            val state = if (currentScrollState == RecyclerView.SCROLL_STATE_IDLE) "idle" else "settling"
            Log.d(TAG, "Contacts scroll state $state. Visible positions [$firstPosition..$lastPosition]")
            contactsAdapter.updateCardNumbers(firstVisiblePosition = firstPosition, lastVisiblePosition = lastPosition)
        } else {
            Log.d(TAG, "Contacts scroll state dragging. Clear card numbers")
            contactsAdapter.clearCardNumbers()
        }
    }

    private fun showContactsNotFoundPlaceholder() {
        recyclerView.isVisible = false

        with(emptyContactsLayout) {
            findViewById<ImageView>(R.id.emptyContactsIcon).setImageResource(R.drawable.ic_round_speech_question)
            findViewById<TextView>(R.id.emptyContactsMessageTitle).setText(R.string.sbdv_no_name_matches)
            findViewById<TextView>(R.id.emptyContactsMessage).setText(R.string.sbdv_who_do_you_want_call)
            isVisible = true
        }
    }

    companion object {
        const val CONTACTS_EXTRA = "contacts"
        const val NAVIGATION_TAG = "contact_search"

        fun hasContactsExtra(context: Context): Boolean {
            val extras = (context as AppCompatActivity).intent.extras
            return extras?.containsKey(CONTACTS_EXTRA) == true
        }
    }
}
