package com.m3u.extension.app

import android.util.Log
import com.m3u.extension.api.Sample
import com.m3u.extension.api.model.EPlaylist
import com.m3u.extension.api.tool.JsonHolder
import com.m3u.extension.api.tool.Logger
import com.m3u.extension.api.tool.OkhttpClientHolder
import com.m3u.extension.api.tool.Saver
import com.m3u.extension.api.workflow.Input
import com.m3u.extension.api.workflow.Resolver
import com.m3u.extension.api.workflow.Workflow
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Sample
class OnlyfansWorkflow(
    /**
     * @see com.m3u.extension.api.workflow.Workflow.AllowedType
     */
    private val okhttpClientHolder: OkhttpClientHolder,
    private val jsonHolder: JsonHolder,
    private val logger: Logger,
    private val saver: Saver
) : Workflow {
    override val name: String = "Onlyfans"
    override val description: String = "This might cost much more time."
    override val inputs: List<Input> = listOf(
        Input(INPUT_LABEL_EMAIL, Input.StringType),
        Input(INPUT_LABEL_PASSWORD, Input.StringType),
        Input(
            INPUT_LABEL_USER_AGENT,
            Input.StringType,
            isOptIn = true
        ),
        Input(INPUT_LABEL_R18_ALLOWED, Input.BooleanType(false))
    )

    override val resolver: Resolver = object : Resolver {
        override suspend fun onResolve(inputs: Map<String, Any>) {
            val email = inputs[INPUT_LABEL_EMAIL] as? String
            val password = inputs[INPUT_LABEL_PASSWORD] as? String
            val userAgent = inputs[INPUT_LABEL_USER_AGENT] as? String
            val isR18Allowed = inputs[INPUT_LABEL_R18_ALLOWED] as? Boolean
            // ...
            Log.e(TAG, "onResolve: inputs=$inputs")
            delay(1.seconds)
            saver.savePlaylist(
                pkgName = this@OnlyfansWorkflow::class.qualifiedName.orEmpty(),
                playlist = EPlaylist(
                    title = "Onlyfans_$email",
                    url = "https://onlyfans.com/$email/$password",
                    userAgent = userAgent.orEmpty(),
                    workflow = this@OnlyfansWorkflow // must pass this!
                )
            )
        }
    }

    companion object {
        private const val INPUT_LABEL_EMAIL = "E-mail"
        private const val INPUT_LABEL_PASSWORD = "Password"
        private const val INPUT_LABEL_USER_AGENT = "User-agent"
        private const val INPUT_LABEL_R18_ALLOWED = "R18 Content allowed"

        private const val TAG = "Onlyfans"
    }
}