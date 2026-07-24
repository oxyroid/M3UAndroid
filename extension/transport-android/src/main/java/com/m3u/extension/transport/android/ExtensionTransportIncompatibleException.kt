package com.m3u.extension.transport.android

import java.io.IOException

class ExtensionTransportIncompatibleException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
