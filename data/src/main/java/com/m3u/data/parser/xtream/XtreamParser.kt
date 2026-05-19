package com.m3u.data.parser.xtream

import kotlinx.coroutines.flow.Flow

typealias XtreamInput = dev.oxyroid.parser.xtream.XtreamInput

internal interface XtreamParser {
	suspend fun getSeriesInfoOrThrow(
		input: XtreamInput,
		seriesId: Int,
	): XtreamChannelInfo

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