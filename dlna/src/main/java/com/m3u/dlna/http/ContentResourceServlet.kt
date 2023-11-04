package com.m3u.dlna.http

import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.util.resource.FileResource
import org.eclipse.jetty.util.resource.Resource
import java.io.File

internal open class ContentResourceServlet : DefaultServlet() {
    override fun getResource(pathInContext: String): Resource? {
        return try {
            File(pathInContext).takeIf { it.exists() }?.let { FileResource.newResource(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    class VideoResourceServlet : ContentResourceServlet()
    class AudioResourceServlet : ContentResourceServlet()
}