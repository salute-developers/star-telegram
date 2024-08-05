package ru.sberdevices.sbdv.network

import kotlinx.coroutines.flow.Flow

enum class NetworkState {
    AVAILABLE,
    NOT_AVAILABLE
}

interface NetworkRepository {
    val networkState: Flow<NetworkState>
}