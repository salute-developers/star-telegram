package ru.sberdevices.telegramcalls.vendor.contacts.data.mapper

import android.net.Uri
import org.telegram.tgnet.TLRPC
import ru.sberdevices.starcontacts.api.vendor.contacts.entity.StarContactDraft
import java.time.LocalDateTime


private const val CALLEE_ID_KEY = "callee_id"

/**
 * @author Ирина Карпенко on 21.10.2021
 */
internal fun TLRPC.User.toStarContactDraft(avatarUri: String?): StarContactDraft {
    return StarContactDraft(
        id = id.toString(),
        firstName = getFirstName(),
        lastName = last_name.orEmpty(),
        callDeeplink = Uri.Builder()
            .appendQueryParameter(CALLEE_ID_KEY, id.toString())
            .build()
            .toString(),
        avatarUri = avatarUri,
        avatarUpdateTime = LocalDateTime.now(),
        additionalParams = null
    )
}

internal fun TLRPC.User.getFirstName(): String {
    val useUserNameAsFirstName = first_name.isNullOrBlank() &&
        last_name.isNullOrBlank() &&
        !username.isNullOrBlank()
    return if (useUserNameAsFirstName) username else first_name.orEmpty()
}
