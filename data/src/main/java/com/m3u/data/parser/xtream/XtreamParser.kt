package com.m3u.data.parser.xtream

import kotlinx.coroutines.flow.Flow

typealias XtreamInput = dev.oxyroid.parser.xtream.XtreamInput

interface XtreamParser {
	suspend fun getSeriesInfoOrThrow(
		input: XtreamInput,
		seriesId: Int,
	): XtreamChannelInfo

	/**
	 * Fetches the synopsis, cast and rating of a single title.
	 *
	 * [kind] selects the endpoint: movies answer on get_vod_info, series on
	 * get_series_info. Both return the descriptive block under `info`.
	 *
	 * Returns null when the server has nothing to say about the title rather
	 * than throwing — a missing synopsis is ordinary, and it must not be
	 * mistaken for the transport failing.
	 */
	suspend fun getChannelDetailsOrNull(
		input: XtreamInput,
		kind: ChannelDetailsKind,
		id: Int,
	): XtreamChannelDetails?

	enum class ChannelDetailsKind { Vod, Series }

	fun parse(input: XtreamInput): Flow<XtreamData>

	suspend fun getInfo(input: XtreamInput): XtreamInfo

	suspend fun getXtreamOutput(input: XtreamInput): XtreamOutput

	companion object {
		fun createInfoUrl(
			basicUrl: String,
			username: String,
			password: String,
			vararg params: Pair<String, Any>,
		): String = dev.oxyroid.parser.xtream.XtreamParser.createInfoUrl(
			basicUrl,
			username,
			password,
			*params,
		)

		fun createXmlUrl(
			basicUrl: String,
			username: String,
			password: String,
		): String = dev.oxyroid.parser.xtream.XtreamParser.createXmlUrl(
			basicUrl,
			username,
			password,
		)

		const val GET_SERIES_INFO_PARAM_ID =
			dev.oxyroid.parser.xtream.XtreamParser.GET_SERIES_INFO_PARAM_ID
	}
}