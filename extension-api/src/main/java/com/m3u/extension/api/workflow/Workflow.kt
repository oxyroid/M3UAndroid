package com.m3u.extension.api.workflow

import com.m3u.extension.api.tool.JsonHolder
import com.m3u.extension.api.tool.Logger
import com.m3u.extension.api.tool.OkhttpClientHolder
import com.m3u.extension.api.tool.Saver
import kotlinx.serialization.json.Json
import kotlin.reflect.KClassifier

/**
 * @sample com.m3u.extension.app.OnlyfansWorkflow
 */
interface Workflow {
    val name: String
    val inputs: List<Input>
    val resolver: Resolver
    val description: String get() = ""

    // only these properties can be passed into the workflow constructor
    enum class AllowedType(val classifier: KClassifier) {
        OKHTTP_CLIENT_HOLDER(OkhttpClientHolder::class),
        JSON_HOLDER(JsonHolder::class),
        LOGGER(Logger::class),
        SAVER(Saver::class);
        companion object {
            val classifiers = AllowedType.entries.map { it.classifier }
        }
    }
}