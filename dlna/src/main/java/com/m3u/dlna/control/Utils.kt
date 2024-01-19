package com.m3u.dlna.control

import android.os.Handler
import android.os.Looper
import org.jupnp.support.model.item.VideoItem

internal object MetadataUtils {
    private val DIDL_LITE_XML = """
        <?xml version="1.0"?>
        <DIDL-Lite 
            xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" 
            xmlns:dc="http://purl.org/dc/elements/1.1/" 
            xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
            %s
        </DIDL-Lite>
    """.trimIndent()

    fun create(url: String, title: String) = DIDL_LITE_XML.format(buildItemXml(url, title))

    private fun buildItemXml(url: String, title: String): String {
        val item = VideoItem(title, "-1", title, null)
        val builder = StringBuilder()
        builder.append("<item id=\"$title\" parentID=\"-1\" restricted=\"1\">")
        builder.append("<dc:title>$title</dc:title>")
        builder.append("<upnp:class>${item.clazz.value}</upnp:class>")
        builder.append("<res protocolInfo=\"http-get:*:video/mp4:*;DLNA.ORG_OP=01;\">$url</res>")
        builder.append("</item>")
        return builder.toString()
    }
}

private val mainHandler = Handler(Looper.getMainLooper())

fun executeInMainThread(runnable: Runnable) {
    if (Thread.currentThread() == Looper.getMainLooper().thread) {
        runnable.run()
    } else {
        mainHandler.post(runnable)
    }
}