package ru.sberdevices.telegramcalls.vendor.user.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.tgnet.TLRPC
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.sbdv.config.Config
import java.io.File

private const val FILE_PROVIDER_AUTHORITY = "${BuildConfig.APPLICATION_ID}.provider"

/**
 * @author Ирина Карпенко on 29.12.2021
 */
interface AvatarLoader {
    fun loadAvatar(user: TLRPC.User): File?
    fun grantUriAccess(uri: String?)
    fun getContentUri(file: File): String?
}

class AvatarLoaderImpl(private val context: Context, private val config: Config) : AvatarLoader {
    private val logger = Logger.get("AvatarLoaderImpl")

    private val mediaDirCacheFile by lazy(LazyThreadSafetyMode.NONE) {
        FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE)
    }
    private val mediaDirCacheUri by lazy(LazyThreadSafetyMode.NONE) {
        FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, mediaDirCacheFile)
    }

    override fun loadAvatar(user: TLRPC.User): File? {
        val avatarLocationKey = ImageLocation
            .getForUserOrChat(user, ImageLocation.TYPE_BIG)
            ?.let { imageLocation ->
                ImageReceiver().run {
                    setImage(imageLocation, null, null, null, user, 0)
                    imageLocation.getKey(parentObject, imageLocation, true).takeUnless { it.isNullOrBlank() }
                }
            }

        val avatarFileUri = avatarLocationKey?.let { "$mediaDirCacheFile/$avatarLocationKey.jpg" }
        val avatarContentUri = avatarLocationKey?.let { "$mediaDirCacheUri/$avatarLocationKey.jpg" }
        logger.verbose { "user ${user.id}: file uri [$avatarFileUri], content uri [$avatarContentUri]" }
        grantUriAccess(avatarContentUri)

        return avatarFileUri?.let { File(it) }
    }

    override fun grantUriAccess(uri: String?) {
        logger.verbose { "grantUriAccess($uri)" }
        if (!uri.isNullOrBlank()) {
            config.packagesAllowedToAccessAvatars.forEach { packageToGrantAccess ->
                context.grantUriPermission(
                    packageToGrantAccess,
                    Uri.parse(uri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    override fun getContentUri(file: File): String? {
        return FileProvider
            .getUriForFile(
                ApplicationLoader.applicationContext,
                FILE_PROVIDER_AUTHORITY,
                file
            )
            ?.toString()
            .takeUnless { it.isNullOrBlank() }
            .also { logger.verbose { "getContentUri(): $it" } }
    }
}