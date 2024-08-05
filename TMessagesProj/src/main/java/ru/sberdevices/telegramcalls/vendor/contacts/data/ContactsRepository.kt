package ru.sberdevices.telegramcalls.vendor.contacts.data

import org.telegram.messenger.ContactsController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.sbdv.contacts.sortWeight
import ru.sberdevices.starcontacts.api.vendor.contacts.entity.StarContactDraft
import java.util.concurrent.ConcurrentHashMap

/**
 * @author Ирина Карпенко on 20.10.2021
 */
interface ContactsRepository {
    val cachedContacts: List<StarContactDraft>

    fun getContact(userId: String): StarContactDraft?
    fun saveContact(contact: StarContactDraft)
    fun saveContacts(contacts: List<StarContactDraft>)
    fun removeContact(id: String)
    fun clearContacts()

    fun fetchUsers()
    fun getUsers(): List<TLRPC.User>
}

internal class ContactsRepositoryImpl : ContactsRepository {
    private val logger = Logger.get("ContactsRepositoryImpl")
    private val _cachedContacts = ConcurrentHashMap<String, StarContactDraft>()

    override val cachedContacts: List<StarContactDraft> get() =  _cachedContacts.values.toList()

    override fun saveContacts(contacts: List<StarContactDraft>) {
        _cachedContacts.putAll(contacts.associateBy { it.id })
    }

    override fun saveContact(contact: StarContactDraft) {
        _cachedContacts[contact.id] = contact
    }

    override fun removeContact(id: String) {
        _cachedContacts.remove(id)
    }

    override fun getContact(userId: String): StarContactDraft? {
        return _cachedContacts[userId]
    }

    override fun clearContacts() {
        _cachedContacts.clear()
    }

    override fun fetchUsers() {
        logger.debug { "fetch users from server" }
        ContactsController.getInstance(UserConfig.selectedAccount).loadContacts(false, 0)
    }

    override fun getUsers(): List<TLRPC.User> {
        logger.debug { "getUsers()" }
        val messagesController: MessagesController = MessagesController.getInstance(UserConfig.selectedAccount)
        return ContactsController.getInstance(UserConfig.selectedAccount).usersSectionsDict.values
            .flatten()
            .map { messagesController.getUser(it.user_id) }
            .filter { user -> user.isValid() }
            .sortedBy { user -> user.sortWeight }
    }
}

private fun TLRPC.User.isValid(): Boolean {
    return !bot && !deleted && !support && !self &&
        (!first_name.isNullOrEmpty() || !last_name.isNullOrEmpty() || !username.isNullOrEmpty())
}
