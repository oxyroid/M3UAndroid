package com.m3u.extension.api.tool

import com.m3u.extension.api.workflow.Workflow

/**
 * Extensions don't need to implement this interface,
 * just pass it into your [Workflow] primary constructor.
 */
interface Logger {
    /**
     * Only worked when user enables extension logging.
     */
    fun log(text: String)
}