package ru.sberdevices.telegramcalls.vendor.authorization.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.starcontacts.vendor.entity.ProfileDto
import ru.sberdevices.telegramcalls.vendor.authorization.data.mapper.toProfileDto
import ru.sberdevices.telegramcalls.vendor.contacts.data.mapper.getFirstName
import ru.sberdevices.telegramcalls.vendor.user.data.AvatarLoader
import java.io.File

/**
 * @author Ирина Карпенко on 20.10.2021
 */
interface AuthorizationRepository {
    val authorized: SharedFlow<Boolean>
    val authorizedProfiles: SharedFlow<List<ProfileDto>>

    fun logout()
    fun refreshProfiles()
    fun loadAvatar(user: TLRPC.User)
    suspend fun setAvatar(file: File)
    suspend fun setUserName(user: TLRPC.User)
}

internal class AuthorizationRepositoryImpl(private val avatarLoader: AvatarLoader) : AuthorizationRepository {
    private val logger by Logger.lazy<AuthorizationRepositoryImpl>()

    private val userConfig: UserConfig
        get() = UserConfig.getInstance(UserConfig.selectedAccount)

    private val _authorized = MutableSharedFlow<Boolean>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val authorized = _authorized.asSharedFlow()

    private val _authorizedProfiles = MutableSharedFlow<List<ProfileDto>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val authorizedProfiles = _authorizedProfiles.asSharedFlow()

    override fun logout() {
        MessagesController.getInstance(UserConfig.selectedAccount).performLogout(1)
    }

    override fun refreshProfiles() {
        logger.debug { "refreshProfiles()" }
        _authorized.tryEmit(
            userConfig.isClientActivated
                .also { logger.debug { "client is${if (it) "" else " not"} activated" } }
        )
        val currentUser = userConfig.currentUser
        logger.debug { "currentUser: $currentUser" }
        if (currentUser != null) {
            val file = avatarLoader.loadAvatar(currentUser)?.takeIf { it.exists() }
            val contentUri = file?.let { avatarLoader.getContentUri(file) }
            val profile = currentUser.toProfileDto(contentUri)
            _authorizedProfiles.tryEmit(listOf(profile))
        } else {
            _authorizedProfiles.tryEmit(emptyList())
        }
    }

    override suspend fun setAvatar(file: File) {
        val uri = avatarLoader.getContentUri(file)
        avatarLoader.grantUriAccess(uri)
        val profile = _authorizedProfiles.first().firstOrNull()
        if (profile != null) {
            logger.debug { "setAvatar($uri)" }
            profile.avatarUri = uri
            _authorizedProfiles.tryEmit(listOf(profile))
        }
    }

    override suspend fun setUserName(user: TLRPC.User) {
        val profile = _authorizedProfiles.first().firstOrNull()
        if (profile != null) {
            logger.debug { "setUserName()" }
            profile.fullName = "${user.getFirstName()} ${user.last_name.orEmpty()}"
            _authorizedProfiles.tryEmit(listOf(profile))
        }
    }

    override fun loadAvatar(user: TLRPC.User) {
        avatarLoader.loadAvatar(user)
    }
}