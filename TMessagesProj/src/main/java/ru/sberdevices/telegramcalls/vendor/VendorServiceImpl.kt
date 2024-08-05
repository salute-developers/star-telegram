@file:Suppress("TooGenericExceptionCaught")

package ru.sberdevices.telegramcalls.vendor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import com.sdkit.base.core.threading.coroutines.CoroutineDispatchers
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.starcontacts.vendor.IAuthorizedProfilesListener
import ru.sberdevices.starcontacts.vendor.IResultListener
import ru.sberdevices.starcontacts.vendor.ITextResultListener
import ru.sberdevices.starcontacts.vendor.IVendorService
import ru.sberdevices.starcontacts.vendor.entity.ErrorDto
import ru.sberdevices.telegramcalls.vendor.authorization.domain.AuthorizationFeature
import ru.sberdevices.telegramcalls.vendor.calls.domain.CallsFeature
import ru.sberdevices.telegramcalls.vendor.contacts.domain.ContactsFeature
import java.util.concurrent.atomic.AtomicReference

/**
 * Имплементация биндера, поддерживающего интерфейс [IVendorService.aidl]
 *
 * @author Ирина Карпенко on 03.09.2021
 */
class VendorServiceImpl(
    coroutineDispatchers: CoroutineDispatchers,
    private val authorizationFeature: AuthorizationFeature,
    private val vendorVersion: String,
    private val contactsFeature: ContactsFeature,
    private val callsFeature: CallsFeature
) : IVendorService.Stub(), AutoCloseable {

    private val logger by Logger.lazy<VendorServiceImpl>()

    private val scope = CoroutineScope(SupervisorJob()) + coroutineDispatchers.io + CoroutineExceptionHandler { _, throwable ->
        logger.warn(throwable) { "unhandled exception" }
    }

    private val refreshRecentCallsJob: AtomicReference<Job?> = AtomicReference(null)
    private val refreshContactsJob: AtomicReference<Job?> = AtomicReference(null)
    private val logoutJob: AtomicReference<Job?> = AtomicReference(null)
    private val sendProfilesJob: AtomicReference<Job?> = AtomicReference(null)

    private val authorizedProfileListener = AtomicReference<IAuthorizedProfilesListener>(null)

    init {
        logger.debug { "init VendorServiceImpl" }
        scope.launch { authorizationFeature.watchRefreshProfilesNotifications() }
        authorizationFeature.authorizedProfiles
            .onEach { profiles ->
                logger.debug { "received ${profiles.count()} profiles" }
                authorizedProfileListener.get()?.onUpdate(profiles)
            }
            .catch {
                if (it is CancellationException) {
                    logger.debug { "authorized profiles subscription cancelled" }
                } else {
                    logger.warn { "authorized profiles subscription error" }
                }
            }
            .onCompletion { logger.debug { "authorized profiles subscription completed" } }
            .launchIn(scope)

        scope.launch {
            authorizationFeature.authorized
                .distinctUntilChanged()
                .catch {
                    if (it is CancellationException) {
                        logger.debug { "authorization subscription cancelled" }
                    } else {
                        logger.warn { "authorization subscription error" }
                    }
                }
                .onCompletion { logger.debug { "authorization subscription completed" } }
                .collectLatest { authorized ->
                    if (authorized) {
                        logger.debug { "authorized, schedule contacts and recent calls sync" }
                        launch { contactsFeature.rescheduleContactsSync() }
                        launch { callsFeature.rescheduleCallsSync() }
                    } else {
                        logger.debug { "unauthorized, cancel contacts and recent calls sync" }
                        launch {
                            contactsFeature.clearContacts()
                            cancelRefreshContacts()
                        }
                        launch {
                            callsFeature.clearCalls()
                            cancelRefreshRecentCalls()
                        }
                    }
                }
        }
    }

    fun refreshProfiles() {
        logger.debug { "refreshProfiles()" }
        authorizationFeature.refreshProfiles()
    }

    override fun refreshRecentCalls(listener: IResultListener) {
        logger.debug { "refresh recent calls" }
        refreshRecentCallsJob.getAndUpdate { job ->
            job?.cancel()
            scope.launch {
                try {
                    logger.debug { "try sync recent calls" }
                    val authorized = authorizationFeature.authorized.first()
                    if (authorized) {
                        logger.debug { "authorized, syncing recent calls" }
                        callsFeature.rescheduleCallsSync()
                    } else {
                        logger.debug { "not authorized to sync recent calls" }
                    }
                    listener.onSuccess()
                } catch (exception: CancellationException) {
                    logger.debug { "refresh recent calls cancelled" }
                    throw exception
                } catch (exception: Exception) {
                    logger.warn(exception) { "refresh recent calls error" }
                    listener.onError(ErrorDto().apply { message = exception.message })
                }
            }
        }
    }

    override fun cancelRefreshRecentCalls() {
        logger.debug { "cancel refresh recent calls" }
        refreshRecentCallsJob.getAndUpdate { job ->
            job?.cancel()
            null
        }
    }

    override fun refreshContacts(listener: IResultListener) {
        logger.debug { "refresh contacts" }
        refreshContactsJob.getAndUpdate { job ->
            job?.cancel()
            scope.launch {
                try {
                    logger.debug { "try sync contacts" }
                    val authorized = authorizationFeature.authorized.first()
                    if (authorized) {
                        logger.debug { "authorized, syncing contacts" }
                        contactsFeature.rescheduleContactsSync()
                    } else {
                        logger.debug { "not authorized to sync contacts" }
                    }
                    listener.onSuccess()
                } catch (exception: CancellationException) {
                    logger.debug { "refresh contacts cancelled" }
                    throw exception
                } catch (exception: Exception) {
                    logger.warn(exception) { "refresh contacts error" }
                    listener.onError(ErrorDto().apply { message = exception.message })
                }
            }
        }
    }

    override fun cancelRefreshContacts() {
        logger.debug { "cancel refresh contacts" }
        refreshContactsJob.getAndUpdate { job ->
            job?.cancel()
            null
        }
    }

    override fun logout(listener: IResultListener) {
        logger.debug { "logout" }
        logoutJob.getAndUpdate { job ->
            job?.cancel()
            scope.launch {
                try {
                    authorizationFeature.logout()
                    listener.onSuccess()
                } catch (exception: CancellationException) {
                    logger.debug { "logout cancelled" }
                    throw exception
                } catch (exception: Exception) {
                    logger.warn(exception) { "logout error" }
                    listener.onError(ErrorDto().apply { message = exception.message })
                }
            }
        }
    }

    override fun cancelLogout() {
        logger.debug { "cancel logout" }
        logoutJob.getAndUpdate { job ->
            job?.cancel()
            null
        }
    }

    override fun subscribeForAuthorizedProfiles(listener: IAuthorizedProfilesListener) {
        logger.debug { "subscribeForAuthorizedProfiles()" }
        sendProfilesJob.getAndUpdate { job ->
            job?.cancel()
            scope.launch {
                try {
                    listener.onUpdate(authorizationFeature.authorizedProfiles.first())
                } catch (exception: CancellationException) {
                    logger.debug { "send profiles job cancelled" }
                    throw exception
                } catch (exception: Exception) {
                    logger.warn(exception) { "send profiles job error" }
                }
            }
        }
        authorizedProfileListener.set(listener)
    }

    override fun unsubscribeFromAuthorizedProfiles() {
        logger.debug { "unsubscribeFromAuthorizedProfiles()" }
        sendProfilesJob.getAndUpdate { job ->
            job?.cancel()
            null
        }
        sendProfilesJob.set(null)
        authorizedProfileListener.set(null)
    }

    override fun getVendorVersion(listener: ITextResultListener) {
        logger.debug { "getVendorVersion()" }
        try {
            listener.onResult(vendorVersion)
        } catch (exception: Exception) {
            logger.warn(exception) { "get vendor version error" }
        }
    }

    override fun cancelGetVendorVersion() {
        logger.debug { "cancelGetVendorVersion()" }
        /** noop */
    }

    override fun close() {
        logger.debug { "close()" }
        authorizedProfileListener.set(null)

        scope.cancel()
    }
}