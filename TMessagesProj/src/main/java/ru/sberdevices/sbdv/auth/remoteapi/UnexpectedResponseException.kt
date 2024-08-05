package ru.sberdevices.sbdv.auth.remoteapi

import org.telegram.tgnet.TLObject

class UnexpectedResponseException(val tlObject: TLObject) : RuntimeException("Unexpected $tlObject")
