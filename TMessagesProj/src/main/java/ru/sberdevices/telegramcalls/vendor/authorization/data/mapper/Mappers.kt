package ru.sberdevices.telegramcalls.vendor.authorization.data.mapper

import android.telephony.PhoneNumberUtils
import org.telegram.tgnet.TLRPC
import ru.sberdevices.starcontacts.vendor.entity.ProfileDto
import ru.sberdevices.telegramcalls.vendor.contacts.data.mapper.getFirstName

/**
 * @author Ирина Карпенко on 21.10.2021
 */
internal fun TLRPC.User.toProfileDto(uri: String?): ProfileDto {
    return ProfileDto().apply {
        fullName = "${getFirstName()} ${last_name.orEmpty()}"
        phone = this@toProfileDto.phone
            .takeUnless { it.isNullOrBlank() }
            ?.let { "+${PhoneNumberUtils.formatNumber(it, "RU")}" }
        avatarUri = uri
    }
}