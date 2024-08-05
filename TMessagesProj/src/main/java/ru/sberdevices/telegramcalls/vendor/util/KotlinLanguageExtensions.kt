package ru.sberdevices.telegramcalls.vendor.util

/**
 * Use to ensure all sealed class cases covered:
 * when (state) {
 *     is A -> { }
 *     is B -> { }
 * }.exhaustive
 */
inline val <reified T> T.exhaustive: T
    get() = this
