package com.m3u.data.contract

import java.security.SecureRandom
import javax.net.ssl.SSLContext

object SSL {
    val TLSTrustAll by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(Certs.TrustAll), SecureRandom())
        }
    }
}
