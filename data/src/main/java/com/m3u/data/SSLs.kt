package com.m3u.data

import java.security.SecureRandom
import javax.net.ssl.SSLContext

internal object SSLs {
    val TLSTrustAll: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(Certs.TrustAll), SecureRandom())
        }
    }
    val SSLTrustAll: SSLContext by lazy {
        SSLContext.getInstance("SSL").apply {
            init(null, arrayOf(Certs.TrustAll), SecureRandom())
        }
    }
}
