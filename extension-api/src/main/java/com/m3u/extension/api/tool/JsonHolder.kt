package com.m3u.extension.api.tool

import com.m3u.extension.api.workflow.Workflow
import kotlinx.serialization.json.Json

/**
 * Extensions don't need to construct this class,
 * just pass it into your [Workflow] primary constructor.
 */
data class JsonHolder(val json: Json)
