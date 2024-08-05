package ru.sberdevices.sbdv.calls

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.R
import ru.sberdevices.sbdv.calls.CallType.IN
import ru.sberdevices.sbdv.calls.CallType.MISSED
import ru.sberdevices.sbdv.calls.CallType.OUT
import ru.sberdevices.sbdv.util.toFormattedString
import ru.sberdevices.sbdv.view.AvatarView

class RecentCallsAdapter(
    context: Context,
    private val itemClickListener: (userId: Long) -> Unit
) : RecyclerView.Adapter<CallHolder>() {

    private val outgoingCallIcon = ContextCompat.getDrawable(context, R.drawable.ic_outgoing_call)!!
    private val incomingCallIcon = ContextCompat.getDrawable(context, R.drawable.ic_incoming_call)!!
    private val missedCallIcon = ContextCompat.getDrawable(context, R.drawable.ic_missed_call)!!

    private val calls: MutableList<CallInfo> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.sbdv_recent_call, parent, false)
        view.tag = view.findViewById<TextView>(R.id.text_number)
        return CallHolder(view, outgoingCallIcon, incomingCallIcon, missedCallIcon)
    }

    override fun getItemCount() = calls.size

    override fun onBindViewHolder(holder: CallHolder, position: Int) {
        holder.bind(
            callInfo = calls[position],
            position = position,
            callsCount = itemCount,
            itemClickListener = itemClickListener
        )
    }

    fun setCalls(calls: List<CallInfo>) {
        if (this.calls != calls) {
            val df = DiffUtil.calculateDiff(DFCallback(calls, this.calls))
            this.calls.clear()
            this.calls.addAll(calls)
            df.dispatchUpdatesTo(this)
        }
    }

    fun getCalls(): List<CallInfo> = this.calls.toList()

    fun updateCardNumbers(firstVisiblePosition: Int, lastVisiblePosition: Int) {
        val updatedContacts = calls.mapIndexed { index, call ->
            call.copy(
                contact = call.contact.copy(
                    displayedNumber = when {
                        index in firstVisiblePosition..lastVisiblePosition -> index + 1 - firstVisiblePosition
                        firstVisiblePosition == RecyclerView.NO_POSITION || lastVisiblePosition == RecyclerView.NO_POSITION -> index + 1
                        else -> null
                    }
                )
            )
        }
        setCalls(updatedContacts)
    }

    fun clearCardNumbers() {
        setCalls(calls.map { it.copy(contact = it.contact.copy(displayedNumber = null)) })
    }
}

class DFCallback(
    private val newCalls: List<CallInfo>,
    private val oldCalls: List<CallInfo>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldCalls.size

    override fun getNewListSize(): Int = newCalls.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldCalls[oldItemPosition].contact.id == newCalls[newItemPosition].contact.id && oldCalls[oldItemPosition].date == newCalls[newItemPosition].date
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
        oldCalls[oldItemPosition] == newCalls[newItemPosition]
}

class CallHolder(
    private val view: View,
    private val incomingCallIcon: Drawable,
    private val outgoingCallIcon: Drawable,
    private val missedCallIcon: Drawable
) : RecyclerView.ViewHolder(view) {

    private val photoView = view.findViewById<AvatarView>(R.id.main_photo)
    private val positionNumberView = view.findViewById<TextView>(R.id.text_number)
    private val nameView = view.findViewById<TextView>(R.id.item_name)
    private val textInfo: TextView = view.findViewById(R.id.item_text_info)
    private val cardBubbleInfo = view.findViewById<CardView>(R.id.card_bubble_info)
    private val cardBubbleInfoText = view.findViewById<TextView>(R.id.card_bubble_info_text)

    private val callStatusIconSize = view.context.resources.getDimensionPixelSize(R.dimen.call_status_icon_size)
    private val callStatusIconPadding = view.context.resources.getDimensionPixelSize(R.dimen.call_status_icon_paddind)
    private val redColor = ContextCompat.getColor(view.context, R.color.card_red)
    private val salmonPearlRedColor = ContextCompat.getColor(view.context, R.color.card_salmon_pearl_red)
    private val grayColor = ContextCompat.getColor(view.context, R.color.card_gray)
    private val textWhiteColor = ContextCompat.getColor(view.context, R.color.card_text_white)

    fun bind(callInfo: CallInfo, position: Int, callsCount: Int, itemClickListener: (userId: Long) -> Unit) {
        val contact = callInfo.contact
        view.id = callInfo.hashCode()
        view.nextFocusLeftId = (if (position == 0) view.id else View.NO_ID)
        view.nextFocusRightId = (if (position == callsCount) view.id else View.NO_ID)
        nameView.text = if (contact.firstName.isNullOrEmpty()) {
            if (contact.lastName.isNullOrEmpty()) {
                contact.user.username
            } else {
                contact.lastName
            }
        } else {
            "${contact.firstName}\n${contact.lastName.orEmpty()}"
        }

        positionNumberView.isVisible = contact.displayedNumber != null
        positionNumberView.text = contact.displayedNumber?.toString()

        photoView.setUser(contact.user)

        val iconDrawable = when (callInfo.type) {
            IN -> incomingCallIcon
            OUT -> outgoingCallIcon
            MISSED -> missedCallIcon
        }
        iconDrawable.setBounds(0, 0, callStatusIconSize, callStatusIconSize)
        textInfo.setCompoundDrawables(iconDrawable, null, null, null)
        textInfo.text = callInfo.date.toFormattedString()
        textInfo.compoundDrawablePadding = callStatusIconPadding

        if (callInfo.type == MISSED) {
            textInfo.setTextColor(salmonPearlRedColor)
        } else {
            textInfo.setTextColor(textWhiteColor)
        }

        if (callInfo.callsCount > 1) {
            cardBubbleInfoText.text = callInfo.callsCount.toString()
            cardBubbleInfo.visibility = View.VISIBLE
            if (callInfo.type == MISSED) {
                cardBubbleInfo.setCardBackgroundColor(redColor)
            } else {
                cardBubbleInfo.setCardBackgroundColor(grayColor)
            }
        } else {
            cardBubbleInfo.visibility = View.INVISIBLE
        }
        view.setOnClickListener { itemClickListener.invoke(contact.id) }
    }
}
