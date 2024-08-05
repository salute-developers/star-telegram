package ru.sberdevices.sbdv.util

import android.util.Log
import ru.sberdevices.analytics.Analytics

private const val TAG = "AnalyticsStub"

class AnalyticsStub : Analytics {

    override fun send(name: String, value: String) {
        Log.w(TAG, "send called on stub")
    }

    override fun getVersion(): Int? {
        return null
    }

    override fun release() {
        Log.w(TAG, "release called on stub")
    }
}
