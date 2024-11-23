package com.m3u.extension.app

import com.m3u.extension.api.Sample
import com.m3u.extension.api.tool.Logger
import com.m3u.extension.api.tool.Saver
import com.m3u.extension.api.workflow.Input
import com.m3u.extension.api.workflow.Resolver
import com.m3u.extension.api.workflow.Workflow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Sample
class OnlyfansWorkflow(
    /**
     * @see com.m3u.extension.api.workflow.Workflow.AllowedType
     */
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val logger: Logger,
    private val saver: Saver
) : Workflow {
    override val name: String = "Onlyfans"
    override val description: String = "This might cost much more time."
    override val inputs: List<Input> = listOf(
        Input(INPUT_LABEL_EMAIL, Input.StringValue),
        Input(INPUT_LABEL_PASSWORD, Input.StringValue),
        Input(
            INPUT_LABEL_USER_AGENT,
            Input.StringValue,
            isOptIn = true
        ),
        Input(INPUT_LABEL_R18_ALLOWED, Input.BooleanValue(false))
    )

    //    @OptIn(ExperimentalSerializationApi::class)
    override val resolver: Resolver = object : Resolver(okHttpClient, json, logger, saver) {
        override suspend fun onResolve(inputs: Map<String, Any>) {
            val email = inputs[INPUT_LABEL_EMAIL] as? String
            val password = inputs[INPUT_LABEL_PASSWORD] as? String
            val userAgent = inputs[INPUT_LABEL_USER_AGENT] as? String
            val isR18Allowed = inputs[INPUT_LABEL_R18_ALLOWED] as? Boolean
            // ...
//            saver.saveChannel(
//               eChannel
//
//            )
        }
    }

    companion object {
        private const val INPUT_LABEL_EMAIL = "E-mail"
        private const val INPUT_LABEL_PASSWORD = "Password"
        private const val INPUT_LABEL_USER_AGENT = "USER_AGENT"
        private const val INPUT_LABEL_R18_ALLOWED = "R18 Content allowed"
    }
}