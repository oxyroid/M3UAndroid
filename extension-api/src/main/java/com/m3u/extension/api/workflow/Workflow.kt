package com.m3u.extension.api.workflow

import com.m3u.extension.api.tool.Logger
import com.m3u.extension.api.tool.Saver
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
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
        OKHTTP_CLIENT(OkHttpClient::class),
        JSON(Json::class),
        LOGGER(Logger::class),
        SAVER(Saver::class);
        companion object {
            val classifiers = AllowedType.entries.map { it.classifier }
        }
    }
}