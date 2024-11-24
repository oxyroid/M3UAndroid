package com.m3u.extension.api.model

import com.m3u.extension.api.workflow.Workflow

data class EPlaylist(
    val url: String,
    val title: String,
    val userAgent: String,
    val workflow: Workflow
) : EMedia
