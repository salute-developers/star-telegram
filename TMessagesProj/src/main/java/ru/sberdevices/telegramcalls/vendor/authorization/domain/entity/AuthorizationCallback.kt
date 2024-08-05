package ru.sberdevices.telegramcalls.vendor.authorization.domain.entity

/**
 * @author Ирина Карпенко on 02.11.2021
 */
fun interface AuthorizationCallback {
    fun onAuthorization()
}