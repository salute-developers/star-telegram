package ru.sberdevices.sbdv.calls

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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import org.telegram.messenger.MessageObject
import org.telegram.messenger.R
import ru.sberdevices.sbdv.SbdvBaseFragment
import ru.sberdevices.sbdv.SbdvServiceLocator
import ru.sberdevices.sbdv.appstate.ScreenState
import ru.sberdevices.sbdv.util.requestFocusOnFistAttachedChildView
import java.util.ArrayList
import kotlin.math.max
import kotlin.math.min

private const val TAG = "RecentCallsFragment"

@Keep
class RecentCallsFragment : SbdvBaseFragment() {
    private val callRepository = SbdvServiceLocator.getCallRepositorySharedInstance()
    private val callsAdapter by lazy { RecentCallsAdapter(requireContext()) { onCallToUserClick(it) } }

    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private var emptyContactsCallsLayout: LinearLayout? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        callRepository.requestRecentCalls()
        return inflater.inflate(R.layout.sbdv_fragment_recent_calls, container, false)
    }

    override fun onNewMessages(messages: ArrayList<MessageObject>) {
        Log.d(TAG, "onNewMessages()")
        callRepository.onNewMessages(messages)
    }

    override fun onDeleteMessages() {
        Log.d(TAG, "onDeleteMessages()")
        callRepository.requestRecentCalls()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        recyclerView = view.findViewById(R.id.recentCallsRecyclerView)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = callsAdapter
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, state: Int) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) {
                    callsAdapter.updateCardNumbers(
                        firstVisiblePosition = layoutManager.findFirstVisibleItemPosition(),
                        lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
                    )
                } else {
                    callsAdapter.clearCardNumbers()
                }
            }
        })

        recyclerView.requestFocusOnFistAttachedChildView()
        recyclerView.requestFocus()

        emptyContactsCallsLayout = view.findViewById(R.id.emptyContactsCallsLayout)

        callRepository.callInfoLiveData.observe(viewLifecycleOwner) { tgCalls ->
            if (tgCalls.isEmpty()) {
                showRecentCallsNotFoundMessage()
            } else {
                setCalls(tgCalls)
            }
        }

        activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume()")

        // delay update a bit so findFirstVisibleItemPosition() findLastVisibleItemPosition() methods work properly
        recyclerView.post {
            if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                callsAdapter.updateCardNumbers(
                    firstVisiblePosition = layoutManager.findFirstVisibleItemPosition(),
                    lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
                )
            }
        }
    }

    private fun setCalls(calls: List<CallInfo>) {
        showRecycler(true)
        callsAdapter.setCalls(calls)
        if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
            callsAdapter.updateCardNumbers(
                firstVisiblePosition = layoutManager.findFirstVisibleItemPosition(),
                lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
            )
        } else {
            callsAdapter.clearCardNumbers()
        }
    }

    private fun showRecentCallsNotFoundMessage() {
        showRecycler(false)
        emptyContactsCallsLayout?.let { layout ->
            layout.findViewById<ImageView>(R.id.emptyContactsIcon)
                .setImageResource(R.drawable.ic_user_on_user)
            layout.findViewById<TextView>(R.id.emptyContactsMessageTitle)
                .setText(R.string.sbdv_no_recent_calls)
            layout.findViewById<TextView>(R.id.emptyContactsMessage)
                .setText(R.string.sbdv_time_to_say_salute)
        }
    }

    private fun showRecycler(show: Boolean) {
        recyclerView.visibility = if (show) View.VISIBLE else View.GONE
        emptyContactsCallsLayout?.visibility = if (show) View.GONE else View.VISIBLE
    }

    override fun onMainUserInfoChange() {
        super.onMainUserInfoChange()
        callRepository.requestRecentCalls()
    }

    override fun onLogout() {
        super.onLogout()
        callRepository.clearCalls()
    }

    fun getScreenState(): ScreenState? {
        Log.v(TAG, "getScreenState(). Fragment is resumed: $isResumed")
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager?
        val firstPosition = layoutManager?.findFirstVisibleItemPosition()
        val lastPosition = layoutManager?.findLastVisibleItemPosition()

        return if (isResumed && firstPosition != null && lastPosition != null && lastPosition != RecyclerView.NO_POSITION) {
            val allContacts = callsAdapter.getCalls().map { it.contact }
            ScreenState(allContacts.subList(max(firstPosition, 0), min(lastPosition + 1, allContacts.size)))
        } else {
            null
        }
    }
}
