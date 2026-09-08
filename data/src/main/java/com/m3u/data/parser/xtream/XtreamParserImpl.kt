package com.m3u.data.parser.xtream

import com.m3u.data.api.OkhttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

/** Xtream names the movie parameter `vod_id`; the submodule only exposes the series one. */
private const val GET_VOD_INFO_PARAM_ID = "vod_id"

internal class XtreamParserImpl @Inject constructor(
	@OkhttpClient(true) private val okHttpClient: OkHttpClient,
) : XtreamParser {
	private val delegate = dev.oxyroid.parser.xtream.XtreamParserImpl(okHttpClient)

	// Panels add fields freely and answer with whatever they please for the
	// ones they do implement; refusing the whole payload over an unexpected
	// key would cost the sheet its content.
	private val json = Json {
		ignoreUnknownKeys = true
		isLenient = true
		explicitNulls = false
		coerceInputValues = true
	}

	override suspend fun getSeriesInfoOrThrow(
		input: XtreamInput,
		seriesId: Int,
	): XtreamChannelInfo = delegate.getSeriesInfoOrThrow(input, seriesId)

	/**
	 * Issued here rather than through the parser submodule, which models the
	 * episode list of get_series_info but not the descriptive block both
	 * endpoints return. Only the URL builder is borrowed from it, so this stays
	 * a single-repository change.
	 */
	override suspend fun getChannelDetailsOrNull(
		input: XtreamInput,
		kind: XtreamParser.ChannelDetailsKind,
		id: Int,
	): XtreamChannelDetails? = withContext(Dispatchers.IO) {
		val (basicUrl, username, password, _) = input
		val action = when (kind) {
			XtreamParser.ChannelDetailsKind.Vod ->
				dev.oxyroid.parser.xtream.XtreamParser.Action.GET_VOD_INFO
			XtreamParser.ChannelDetailsKind.Series ->
				dev.oxyroid.parser.xtream.XtreamParser.Action.GET_SERIES_INFO
		}
		val parameter = when (kind) {
			XtreamParser.ChannelDetailsKind.Vod -> GET_VOD_INFO_PARAM_ID
			XtreamParser.ChannelDetailsKind.Series -> XtreamParser.GET_SERIES_INFO_PARAM_ID
		}
		val url = dev.oxyroid.parser.xtream.XtreamParser.createActionUrl(
			basicUrl,
			username,
			password,
			action,
			parameter to id,
		)
		// Never log this URL or the request: it carries the account credentials
		// in its query string.
		runCatching {
			val request = Request.Builder().url(url).build()
			okHttpClient.newCall(request).execute().use { response ->
				if (!response.isSuccessful) return@use null
				val body = response.body?.string().orEmpty()
				if (body.isBlank()) return@use null
				json.decodeFromString<XtreamChannelDetails>(body)
			}
		}.getOrNull()?.takeIf { details -> details.info != null }
	}

	override fun parse(input: XtreamInput): Flow<XtreamData> =
		delegate.parse(input)
			.asFlow()
			.flowOn(Dispatchers.Default)

	override suspend fun getInfo(input: XtreamInput): XtreamInfo = delegate.getInfo(input)

	override suspend fun getXtreamOutput(input: XtreamInput): XtreamOutput =
		delegate.getXtreamOutput(input)
}