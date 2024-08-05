package ru.sberdevices.sbdv.contacts

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.Keep
import androidx.annotation.MainThread
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_SETTLING
import androidx.recyclerview.widget.SimpleItemAnimator
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.telegram.messenger.ContactsController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import ru.sberdevices.sbdv.SbdvBaseFragment
import ru.sberdevices.sbdv.SbdvServiceLocator
import ru.sberdevices.sbdv.appstate.ScreenState
import ru.sberdevices.sbdv.model.Contact
import ru.sberdevices.sbdv.util.isSameList
import ru.sberdevices.sbdv.view.alphabeticrecyclerview.AlphabeticRecyclerView
import ru.sberdevices.sbdv.view.alphabeticrecyclerview.CenterSmoothScroller
import ru.sberdevices.services.calls.CallManager
import kotlin.math.max
import kotlin.math.min

private const val TAG = "ContactsFragment"
private const val ALLOWED_QUICK_SCROLL_LENGTH = 7

@Keep
class ContactsFragment : SbdvBaseFragment() {
    private var lettersFocusRecentlyChanged: Boolean? = null
        set(value) {
            if (value != field && value != null) {
                updateLettersAppearance(value)
                field = value
            }
        }

    private val contactsRepository by lazy { SbdvServiceLocator.getContactsRepositorySharedInstance() }

    private val contactsAdapter: ContactsAdapter = ContactsAdapter(
        { position, _ ->
            val layoutManager = contactsRecyclerView?.layoutManager as LinearLayoutManager
            val firstVisibleIndex = layoutManager.findFirstVisibleItemPosition()

            /** can return real value minus 1 */
            val lastVisibleIndex = layoutManager.findLastVisibleItemPosition()
            if (firstVisibleIndex < 0 || lastVisibleIndex <= 0) {
                return@ContactsAdapter
            }
            val itemsCount = contactsRecyclerView?.adapter?.itemCount ?: 0
            val index = when {
                firstVisibleIndex == 0 -> firstVisibleIndex
                lastVisibleIndex == (itemsCount - 1) -> lastVisibleIndex
                position == (itemsCount - 1) -> position
                else -> (firstVisibleIndex + lastVisibleIndex) / 2
            }
            if (index >= 0) {
                val adapter = contactsRecyclerView?.adapter as ContactsAdapter
                val contact = adapter.getContactByPosition(index)
                val firstLetter = contact.firstNameLetter
                alphabeticRecyclerView?.let { recyclerView ->
                    if (lastTouchedRecyclerView == contactsRecyclerView || lettersFocusRecentlyChanged == false) {
                        if (firstLetter != null) {
                            recyclerView.setPosition(firstLetter.toString())
                        } else {
                            recyclerView.setUnknownPosition()
                        }
                    }
                }
            }
        },
        { onCallToUserClick(it) },
        { _, _ -> lettersFocusRecentlyChanged = false }
    )

    private var contactsRecyclerView: RecyclerView? = null
    private lateinit var layoutManager: LinearLayoutManager

    private var alphabeticRecyclerView: AlphabeticRecyclerView? = null

    private var emptyContactsLayout: LinearLayout? = null

    /** help synchronize scrolling of two recyclerviews */
    private var lastTouchedRecyclerView: RecyclerView? = null
    private lateinit var startAlphabeticRecyclerViewArrowImageView: ImageView
    private lateinit var endAlphabeticRecyclerViewArrowImageView: ImageView
    private lateinit var centerLetterBackgroundImageView: ImageView

    private var callsManager: CallManager? = null
    private val config = SbdvServiceLocator.config

    private val contactsController: ContactsController by lazy {
        ContactsController.getInstance(UserConfig.selectedAccount)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.sbdv_fragment_contacts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        startAlphabeticRecyclerViewArrowImageView = view.findViewById(R.id.arrowStartImageView)
        endAlphabeticRecyclerViewArrowImageView = view.findViewById(R.id.arrowEndImageView)
        centerLetterBackgroundImageView = view.findViewById(R.id.centerLetterBackgroundImageView)
        updateLettersAppearance(showFocusRelatedViews = false)

        contactsRecyclerView = view.findViewById<RecyclerView>(R.id.contactsRecyclerView).apply {
            layoutManager = this@ContactsFragment.layoutManager
            adapter = contactsAdapter
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, state: Int) {
                    if (state == SCROLL_STATE_IDLE) {
                        contactsAdapter.updateCardNumbers(
                            firstVisiblePosition = this@ContactsFragment.layoutManager.findFirstVisibleItemPosition(),
                            lastVisiblePosition = this@ContactsFragment.layoutManager.findLastVisibleItemPosition()
                        )
                    } else {
                        contactsAdapter.clearCardNumbers()
                    }

                    updateLastTouchedRecyclerView()
                }
            })
        }

        alphabeticRecyclerView = view.findViewById<AlphabeticRecyclerView>(R.id.alphabeticRecyclerView).apply {

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, state: Int) {
                    updateLastTouchedRecyclerView()
                    super.onScrollStateChanged(recyclerView, state)
                }
            })

            setPositionListener(object : AlphabeticRecyclerView.Listener {
                override fun onPositionChange(position: Int, character: String) {
                    if (lastTouchedRecyclerView == alphabeticRecyclerView || lettersFocusRecentlyChanged == true) {
                        val firstItemPosition = this@ContactsFragment.layoutManager.findFirstVisibleItemPosition()
                        val lastItemPosition = this@ContactsFragment.layoutManager.findLastVisibleItemPosition()
                        val targetPosition = contactsAdapter.getFirstIndexByFirstLetter(character)
                        val isCloseTarget = targetPosition in firstItemPosition..lastItemPosition
                        if (isCloseTarget) {
                            scrollContactsToLetter(character)
                        } else {
                            quickScrollContactsToLetter(character)
                        }
                    }
                    startAlphabeticRecyclerViewArrowImageView.isVisible = lettersFocusRecentlyChanged == true && alphabeticRecyclerView?.isFirstItemSelected == false
                    endAlphabeticRecyclerViewArrowImageView.isVisible = lettersFocusRecentlyChanged == true && alphabeticRecyclerView?.isLastItemSelected == false
                }

                override fun onItemClicked(position: Int, character: String) {
                    Log.d(TAG, "onItemClicked($position, $character)")
                    quickScrollContactsToLetter(character)
                }

                override fun onFocusChange(view: View, hasFocus: Boolean) {
                    lettersFocusRecentlyChanged = true
                }
            })
        }

        emptyContactsLayout = view.findViewById(R.id.emptyContactsLayout)

        config.integratedWithSingleCallsPlaceFlow
            .onEach { integrated ->
                if (integrated) {
                    callsManager?.close()
                    callsManager = null
                } else {
                    if (callsManager == null) {
                        callsManager = SbdvServiceLocator.getCallManager()
                    }
                    callsManager?.setContacts(
                        contactsRepository.contacts.value.orEmpty().map {
                            ru.sberdevices.services.calls.Contact(
                                id = it.id.toString(),
                                firstName = it.firstName,
                                lastName = it.lastName
                            )
                        }
                    )
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        contactsRepository.contacts.observe(viewLifecycleOwner, { contacts ->
            updateContactsList(contacts)
        })

        contactsController.reloadContactsStatusesMaybe(true)

        activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
    }

    override fun onDestroy() {
        super.onDestroy()
        callsManager?.close()
    }

    fun getScreenState(): ScreenState? {
        Log.v(TAG, "getScreenState(). Fragment is resumed: $isResumed")
        val layoutManager = contactsRecyclerView?.layoutManager as LinearLayoutManager?
        val firstPosition = layoutManager?.findFirstVisibleItemPosition()
        val lastPosition = layoutManager?.findLastVisibleItemPosition()

        return if (isResumed && firstPosition != null && lastPosition != null && lastPosition != RecyclerView.NO_POSITION) {
            val allContacts = contactsAdapter.getContacts()
            ScreenState(allContacts.subList(max(firstPosition, 0), min(lastPosition + 1, allContacts.size)))
        } else {
            null
        }
    }

    private fun updateLettersAppearance(showFocusRelatedViews: Boolean) {
        startAlphabeticRecyclerViewArrowImageView.isVisible = showFocusRelatedViews && alphabeticRecyclerView?.isFirstItemSelected == false
        endAlphabeticRecyclerViewArrowImageView.isVisible = showFocusRelatedViews && alphabeticRecyclerView?.isLastItemSelected == false
        alphabeticRecyclerView?.ownsFocus = showFocusRelatedViews
        centerLetterBackgroundImageView.isVisible = showFocusRelatedViews
    }

    @MainThread
    private fun scrollContactsToLetter(character: String) {
        Log.d(TAG, "scrollContactsToLetter($character)")
        val cardIndex = contactsAdapter.getFirstIndexByFirstLetter(character)
        cardIndex?.let { index ->
            contactsRecyclerView?.let { recyclerView ->
                val smoothScroller = CenterSmoothScroller(recyclerView.context)
                smoothScroller.targetPosition = index
                recyclerView.layoutManager?.startSmoothScroll(smoothScroller)
            }
        }
    }

    @MainThread
    private fun quickScrollContactsToLetter(targetLetter: String) {
        val smoothScroller = CenterSmoothScroller(requireActivity())
        val firstItemPosition = this@ContactsFragment.layoutManager.findFirstVisibleItemPosition()
        val lastItemPosition = this@ContactsFragment.layoutManager.findLastVisibleItemPosition()
        val targetPosition = contactsAdapter.getFirstIndexByFirstLetter(targetLetter)

        if (targetPosition != null) {
            val jumpTargetPosition = determineJumpTarget(targetPosition, firstItemPosition, lastItemPosition)
            Log.d(
                TAG,
                "Quick scroll to $targetLetter ($targetPosition) from visible items[$firstItemPosition, $lastItemPosition]. Jump through $jumpTargetPosition"
            )
            if (jumpTargetPosition != null && jumpTargetPosition > 0) this@ContactsFragment.layoutManager.scrollToPosition(
                jumpTargetPosition
            )
            smoothScroller.targetPosition = targetPosition
            layoutManager.startSmoothScroll(smoothScroller)
        }
    }

    private fun determineJumpTarget(targetPosition: Int, firstItemPosition: Int, lastItemPosition: Int): Int? {
        return when {
            targetPosition >= lastItemPosition && targetPosition - lastItemPosition < ALLOWED_QUICK_SCROLL_LENGTH -> null
            targetPosition <= firstItemPosition && firstItemPosition - targetPosition < ALLOWED_QUICK_SCROLL_LENGTH -> null
            targetPosition > lastItemPosition -> targetPosition - ALLOWED_QUICK_SCROLL_LENGTH
            targetPosition < firstItemPosition -> targetPosition + ALLOWED_QUICK_SCROLL_LENGTH
            else -> null
        }
    }

    @MainThread
    private fun updateLastTouchedRecyclerView() {
        val contactsRecyclerViewScrollState = contactsRecyclerView?.scrollState ?: SCROLL_STATE_IDLE
        val alphabeticRecyclerViewScrollState = alphabeticRecyclerView?.scrollState
            ?: SCROLL_STATE_IDLE
        if (alphabeticRecyclerViewScrollState == SCROLL_STATE_DRAGGING) {
            lastTouchedRecyclerView = alphabeticRecyclerView
            return
        }
        if (contactsRecyclerViewScrollState == SCROLL_STATE_DRAGGING) {
            lastTouchedRecyclerView = contactsRecyclerView
            return
        }
        if (contactsRecyclerViewScrollState == SCROLL_STATE_IDLE && alphabeticRecyclerViewScrollState == SCROLL_STATE_IDLE) {
            lastTouchedRecyclerView = null
        }
    }

    @MainThread
    private fun updateContactsList(contacts: List<Contact>) {
        Log.d(TAG, "updateContactsList(${contacts.size} contacts)")
        if (contacts.isEmpty()) {
            setRecyclersVisible(false)
            showContactsNotFoundMessage()
        } else {
            val contactsFromAdapter = contactsAdapter.getContacts()
            val theSameList = contactsFromAdapter.isSameList(contacts)
            if (theSameList) return
            if (!config.integratedWithSingleCallsPlace) {
                if (callsManager == null) {
                    callsManager = SbdvServiceLocator.getCallManager()
                }
                callsManager?.setContacts(contacts.map {
                    ru.sberdevices.services.calls.Contact(it.id.toString(), it.firstName, it.lastName)
                })
            }

            // delay update a bit so findFirstVisibleItemPosition() findLastVisibleItemPosition() methods work properly
            contactsRecyclerView?.post { showContacts(contacts) }
        }
    }

    private fun showContacts(contacts: List<Contact>) {
        Log.d(TAG, "showContacts(); ${contacts.size} contacts")
        hideContactsNotFoundMessage()
        setRecyclersVisible(true)
        contactsAdapter.setContacts(contacts, false)
        val firstNameLetters = contacts.firstNameLetters()
        alphabeticRecyclerView?.setCustomAlphabet(firstNameLetters)

        val currentScrollState = contactsRecyclerView?.scrollState ?: SCROLL_STATE_IDLE
        if (currentScrollState == SCROLL_STATE_IDLE || currentScrollState == SCROLL_STATE_SETTLING) {
            val firstPosition = this@ContactsFragment.layoutManager.findFirstVisibleItemPosition()
            val lastPosition = this@ContactsFragment.layoutManager.findLastVisibleItemPosition()

            val state = if (currentScrollState == SCROLL_STATE_IDLE) "idle" else "settling"
            Log.d(TAG, "Contacts scroll state $state. Visible positions [$firstPosition..$lastPosition]")
            contactsAdapter.updateCardNumbers(
                firstVisiblePosition = firstPosition,
                lastVisiblePosition = lastPosition
            )
        } else {
            Log.d(TAG, "Contacts scroll state dragging. Clear card numbers")
            contactsAdapter.clearCardNumbers()
        }
    }

    private fun showContactsNotFoundMessage() {
        emptyContactsLayout?.let { layout ->
            layout.findViewById<ImageView>(R.id.emptyContactsIcon).setImageResource(R.drawable.ic_user_on_user)
            layout.findViewById<TextView>(R.id.emptyContactsMessageTitle).setText(R.string.sbdv_no_contacts)
            layout.findViewById<TextView>(R.id.emptyContactsMessage).text = ""
            layout.isVisible = true
        }
    }

    private fun hideContactsNotFoundMessage() {
        emptyContactsLayout?.isVisible = false
    }

    private fun setRecyclersVisible(show: Boolean) {
        Log.d(TAG, "setRecyclersVisible($show)")
        contactsRecyclerView?.isVisible = show
        alphabeticRecyclerView?.isVisible = show
    }
}
