package ru.sberdevices.sbdv.view.alphabeticrecyclerview

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.R

/**
 *  Make scroll to target item to center of recyclerview
 */
class CenterSmoothScroller(context: Context) : LinearSmoothScroller(context) {

    var targetLetter: String? = null

    override fun onTargetFound(targetView: View, state: RecyclerView.State, action: Action) {
        super.onTargetFound(targetView, state, action)
        targetLetter = targetView.findViewById<TextView>(R.id.letterTextView)?.text?.toString()
    }

    override fun calculateDtToFit(
        viewStart: Int,
        viewEnd: Int,
        boxStart: Int,
        boxEnd: Int,
        snapPreference: Int
    ): Int = (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
}
