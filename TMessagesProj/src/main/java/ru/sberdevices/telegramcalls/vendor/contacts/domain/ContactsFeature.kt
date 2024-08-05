package ru.sberdevices.telegramcalls.vendor.contacts.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.telegram.tgnet.TLRPC
import com.sdkit.base.core.threading.coroutines.CoroutineDispatchers
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.sbdv.config.Config
import ru.sberdevices.starcontacts.api.vendor.contacts.StarContactsDestination
import ru.sberdevices.telegramcalls.vendor.contacts.data.ContactsNotificationsRepository
import ru.sberdevices.telegramcalls.vendor.contacts.data.ContactsRepository
import ru.sberdevices.telegramcalls.vendor.contacts.data.mapper.getFirstName
import ru.sberdevices.telegramcalls.vendor.contacts.data.mapper.toStarContactDraft
import ru.sberdevices.telegramcalls.vendor.contacts.domain.entity.ContactsNotification
import ru.sberdevices.telegramcalls.vendor.user.data.AvatarLoader
import ru.sberdevices.telegramcalls.vendor.util.exhaustive
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * @author Ирина Карпенко on 20.10.2021
 */
interface ContactsFeature {
    val loaded: Flow<Boolean>

    fun clearContacts()
    suspend fun refreshContacts()
    suspend fun rescheduleContactsSync()
}

internal class ContactsFeatureImpl(
    private val contactsRepository: ContactsRepository,
    private val contactsNotificationsRepository: ContactsNotificationsRepository,
    private val contactsDestination: StarContactsDestination,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val config: Config,
    private val avatarLoader: AvatarLoader
) : ContactsFeature {
    private val logger by Logger.lazy<ContactsFeatureImpl>()
    private val _loaded = MutableStateFlow(false)

    override val loaded: Flow<Boolean> = _loaded.asStateFlow()

    override suspend fun rescheduleContactsSync() = withContext(coroutineDispatchers.io) {
        logger.debug { "rescheduleContactsSync()" }
        launch {
            config.contactsForceRefreshIntervalMinutesFlow
                .collectLatest { intervalMinutes ->
                    if (intervalMinutes > 0) {
                        logger.debug { "start periodic contacts refreshing with interval: $intervalMinutes minutes" }
                        while (isActive) {
                            delay(TimeUnit.MINUTES.toMillis(intervalMinutes))
                            logger.debug { "periodic contacts refreshing" }
                            refreshContacts()
                        }
                    } else {
                        logger.debug { "stop periodic contacts refreshing" }
                    }
                }
        }
        launch {
            logger.verbose { "${contactsRepository.cachedContacts.count()} contacts in cache" }
            combine(
                config.packagesAllowedToAccessAvatarsFlow,
                loaded
            ) { packages, loaded -> packages to loaded }
                .collectLatest { (packages, loaded) ->
                    if (loaded) {
                        logger.debug { "grant access to avatars: $packages" }
                        contactsRepository.cachedContacts
                            .mapNotNull { contact -> contact.avatarUri }
                            .forEach { avatarUri -> avatarLoader.grantUriAccess(avatarUri) }
                    }
                }
        }
        launch {
            contactsNotificationsRepository.notifications.collect {
                when (it) {
                    is ContactsNotification.Refresh -> refreshContacts()
                    is ContactsNotification.ReceiveUserAvatar -> receiveContactAvatar(it.file)
                    is ContactsNotification.ChangeUserAvatar -> changeContactAvatar(it.user)
                    is ContactsNotification.ChangeUserName -> changeUserName(it.user)
                    is ContactsNotification.AddUser -> addUser(it.user)
                    is ContactsNotification.RemoveUser -> removeUser(it.user)
                }.exhaustive
            }
        }
        contactsRepository.fetchUsers()
    }

    override fun clearContacts() {
        logger.debug { "clearContacts()" }
        contactsDestination.clear()
        _loaded.tryEmit(false)
    }

    override suspend fun refreshContacts() = withContext(coroutineDispatchers.io) {
        logger.debug { "refreshContacts()" }
        try {
            val users = contactsRepository.getUsers()
            logger.debug { "got ${users.count()} users" }
            val contacts = users.map { user ->
                val file = avatarLoader.loadAvatar(user)
                val contentUri = file?.let { avatarLoader.getContentUri(file) }
                user.toStarContactDraft(contentUri)
            }
            contactsDestination.addOrReplace(contacts)
            contactsRepository.saveContacts(contacts)
            _loaded.emit(true)
        } catch (exception: CancellationException) {
            logger.debug { "refreshContacts() cancelled" }
            throw exception
        } catch (exception: Exception) {
            logger.warn(exception) { "refreshContacts() error" }
        } finally {
            logger.debug { "refreshContacts() completed" }
        }
    }

    private fun receiveContactAvatar(file: File) {
        logger.debug { "receiveContactAvatar($file)" }
        val contentUri = avatarLoader.getContentUri(file)
        val contact = contactsRepository.cachedContacts.find { it.avatarUri == contentUri }
            ?: return
        val userId = contact.id
        logger.debug { "updateContactAvatar($userId): $contentUri" }
        try {
            val updatedContact = contact.copy(
                avatarUpdateTime = LocalDateTime.now(),
                avatarUri = contentUri
            )
            contactsDestination.addOrReplace(updatedContact)
            contactsRepository.saveContact(updatedContact)
        } catch (exception: Exception) {
            logger.warn(exception) { "updateContactAvatar($userId) error" }
        } finally {
            logger.debug { "updateContactAvatar($userId) completed" }
        }
    }

    private fun changeContactAvatar(user: TLRPC.User) {
        logger.debug { "changeContactAvatar(${user.id})" }
        val contact = contactsRepository.getContact(user.id.toString()) ?: return
        val file = avatarLoader.loadAvatar(user)
        logger.debug { "${user.id} file: $file" }
        val contentUri = file?.let { avatarLoader.getContentUri(file) }
        val updatedContact = contact.copy(
            avatarUri = contentUri
        )
        contactsDestination.addOrReplace(updatedContact)
        contactsRepository.saveContact(updatedContact)
    }

    private fun changeUserName(user: TLRPC.User) {
        logger.debug { "changeUserName(${user.id})" }
        val contact = contactsRepository.getContact(user.id.toString()) ?: return
        val updatedContact = contact.copy(
            firstName = user.getFirstName(),
            lastName = user.last_name.orEmpty()
        )
        contactsDestination.addOrReplace(updatedContact)
        contactsRepository.saveContact(updatedContact)
    }

    private fun addUser(user: TLRPC.User) {
        logger.debug { "addUser(${user.id})" }
        val file = avatarLoader.loadAvatar(user)
        val contentUri = file?.let { avatarLoader.getContentUri(file) }
        val contact = user.toStarContactDraft(contentUri)
        contactsDestination.addOrReplace(contact)
        contactsRepository.saveContact(contact)
    }

    private fun removeUser(user: TLRPC.User) {
        val id = user.id.toString()
        logger.debug { "removeUser($id)" }

        contactsDestination.remove(id)
        contactsRepository.removeContact(id)
    }
}