package ru.sberdevices.sbdv.model

enum class AppEvent {
    OPEN_APP_UI,

    OPEN_INTRODUCE_SCREEN,
    OPEN_LOGIN_SCREEN,
    LOGIN_PHONE_INSERTED,
    LOGIN_VERIFICATION_CODE_INSERTED,
    LOGIN_PASSWORD_INSERTED,
    SUCCESS_LOGIN,
    LOGOUT,

    OPEN_MAIN_SCREEN,
    CLICK_CONTACTS_LIST,
    CLICK_RECENT_LIST,
    SHOW_VOICE_SEARCH_RESULT_LIST,

    LOAD_CONTACTS,
    LOAD_CONTACTS_FROM_CACHE,
    LOAD_CONTACTS_FROM_SERVER,
    RECEIVED_CONTACTS,
    RECEIVED_EMPTY_CONTACTS,
}