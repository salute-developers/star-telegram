package ru.sberdevices.sbdv.util

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.sberdevices.services.spotter.state.SpotterStateRepository
import ru.sberdevices.services.spotter.state.entities.SpotterState

private const val TAG = "SpotterStateRepositoryStub"

class SpotterStateRepositoryStub : SpotterStateRepository {

    override val spotterStateFlow: Flow<SpotterState> = MutableStateFlow(SpotterState.UNKNOWN)

    override fun setSpotterActive(isActive: Boolean) {
        Log.w(TAG, "setSpotterActive called on stub")
    }

    override fun dispose() {
        Log.w(TAG, "dispose called on stub")
    }
}
