package ru.sberdevices.sbdv.auth.view

import org.telegram.tgnet.TLRPC

data class LoginToken(val authorization: TLRPC.TL_auth_authorization)
