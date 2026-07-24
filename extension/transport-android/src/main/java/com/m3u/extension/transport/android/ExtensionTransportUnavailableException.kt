package com.m3u.extension.transport.android

import java.io.IOException

class ExtensionTransportUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
