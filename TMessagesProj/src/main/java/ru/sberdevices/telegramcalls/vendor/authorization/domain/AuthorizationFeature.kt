package ru.sberdevices.telegramcalls.vendor.authorization.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sdkit.base.core.threading.coroutines.CoroutineDispatchers
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.starcontacts.vendor.entity.ProfileDto
import ru.sberdevices.telegramcalls.vendor.authorization.data.AuthorizationNotificationsRepository
import ru.sberdevices.telegramcalls.vendor.authorization.data.AuthorizationRepository
import ru.sberdevices.telegramcalls.vendor.authorization.domain.entity.AuthorizationCallback
import ru.sberdevices.telegramcalls.vendor.authorization.domain.entity.AuthorizationNotification
import ru.sberdevices.telegramcalls.vendor.calls.domain.CallsFeature
import ru.sberdevices.telegramcalls.vendor.contacts.domain.ContactsFeature
import ru.sberdevices.telegramcalls.vendor.util.exhaustive

/**
 * @author Ирина Карпенко on 01.10.2021
 */
interface AuthorizationFeature {
    val authorized: Flow<Boolean>
    val authorizedProfiles: Flow<List<ProfileDto>>

    suspend fun watchRefreshProfilesNotifications()
    fun logout()
    fun refreshProfiles()

    /** Для использования в Java */
    fun setAuthorizationCallback(callback: AuthorizationCallback)
}

internal class AuthorizationFeatureImpl(
    private val coroutineScope: CoroutineScope,
    private val authorizationNotificationsRepository: AuthorizationNotificationsRepository,
    private val authorizationRepository: AuthorizationRepository,
    private val contactsFeature: ContactsFeature,
    private val callsFeature: CallsFeature,
    private val coroutineDispatchers: CoroutineDispatchers
) : AuthorizationFeature {

    private val logger by Logger.lazy<AuthorizationFeatureImpl>()

    override val authorized: Flow<Boolean> = authorizationRepository.authorized
    override val authorizedProfiles: Flow<List<ProfileDto>> = authorizationRepository.authorizedProfiles

    override suspend fun watchRefreshProfilesNotifications() = withContext(coroutineDispatchers.io) {
        logger.debug { "watchRefreshProfilesNotifications()" }
        authorizationNotificationsRepository.notifications.collect {
            logger.debug { "collected notification: $it" }
            when (it) {
                is AuthorizationNotification.RefreshProfiles -> authorizationRepository.refreshProfiles()
                is AuthorizationNotification.ChangeUserAvatar -> authorizationRepository.loadAvatar(it.user)
                is AuthorizationNotification.ChangeUserName -> authorizationRepository.setUserName(it.user)
                is AuthorizationNotification.ReceiveUserAvatar -> authorizationRepository.setAvatar(it.file)
            }.exhaustive
        }
    }

    override fun refreshProfiles() {
        logger.debug { "refreshProfiles()" }
        authorizationRepository.refreshProfiles()
    }

    override fun logout() {
        logger.debug { "logout()" }
        authorizationRepository.logout()
    }

    override fun setAuthorizationCallback(callback: AuthorizationCallback) {
        logger.debug { "setAuthorizationCallback()" }
        coroutineScope.launch {
            combine(
                authorizedProfiles.map { it.isNotEmpty() }.filter { it }.distinctUntilChanged().onEach { logger.debug { "authorized" } },
                contactsFeature.loaded.filter { it }.onEach { logger.debug { "contacts loaded" } },
                callsFeature.loaded.filter { it }.onEach { logger.debug { "calls loaded" } }
            ) { authorized, loadedContacts, loadedCalls ->
                authorized && loadedContacts && loadedCalls
            }.first()
            logger.debug { "authorization is complete, invoke callback" }
            callback.onAuthorization()
        }
    }
}