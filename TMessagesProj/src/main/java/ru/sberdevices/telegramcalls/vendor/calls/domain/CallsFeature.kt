package ru.sberdevices.telegramcalls.vendor.calls.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import com.sdkit.base.core.threading.coroutines.CoroutineDispatchers
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.starcontacts.api.vendor.calls.StarCallLogDestination
import ru.sberdevices.starcontacts.api.vendor.calls.entity.CallDraft
import ru.sberdevices.telegramcalls.vendor.calls.data.CallsNotificationsRepository
import ru.sberdevices.telegramcalls.vendor.calls.data.CallsRepository
import ru.sberdevices.telegramcalls.vendor.calls.domain.entity.CallsNotification
import ru.sberdevices.telegramcalls.vendor.util.exhaustive

private const val DEFAULT_CALLS_COUNT = 50

/**
 * @author Ирина Карпенко on 20.10.2021
 */
interface CallsFeature {
    val loaded: Flow<Boolean>

    fun clearCalls()
    suspend fun refreshCalls()
    suspend fun rescheduleCallsSync()
}

internal class CallsFeatureImpl(
    private val callsNotificationsRepository: CallsNotificationsRepository,
    private val callsRepository: CallsRepository,
    private val callsDestination: StarCallLogDestination,
    private val coroutineDispatchers: CoroutineDispatchers
) : CallsFeature {
    private val logger by Logger.lazy<CallsFeatureImpl>()
    private val _loaded = MutableStateFlow(false)

    override val loaded: Flow<Boolean> = _loaded.asStateFlow()

    override suspend fun rescheduleCallsSync() = withContext(coroutineDispatchers.io) {
        logger.debug { "rescheduleCallsSync()" }
        refreshCalls()
        callsNotificationsRepository.notifications.collect {
            when (it) {
                is CallsNotification.NewCalls -> addCalls(it.calls)
                is CallsNotification.Refresh -> refreshCalls()
            }.exhaustive
        }
    }

    private fun addCalls(calls: List<CallDraft>) {
        logger.debug { "addCalls()" }
        calls.forEach { call -> callsDestination.addOrReplace(call) }
    }

    override fun clearCalls() {
        logger.debug { "clearCalls()" }
        callsDestination.clear()
        _loaded.tryEmit(false)
    }

    override suspend fun refreshCalls() = withContext(coroutineDispatchers.io) {
        try {
            logger.debug { "refreshCalls()" }
            val calls = callsRepository.requestRecentCalls(DEFAULT_CALLS_COUNT)
            callsDestination.addOrReplace(calls)
            _loaded.emit(true)
        } catch (exception: CancellationException) {
            logger.debug { "refresh calls cancelled" }
            throw exception
        }
    }
}