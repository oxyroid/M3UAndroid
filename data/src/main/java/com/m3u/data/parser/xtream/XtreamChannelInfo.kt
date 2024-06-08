@file:Suppress("unused")

package com.m3u.data.parser.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamChannelInfo(
    @SerialName("episodes")
    val episodes: Map<String, List<Episode>> = emptyMap(),
//    @SerialName("info")
//    val info: Info?,
//    @SerialName("seasons")
//    val seasons: List<String?> = emptyList()
) {
    @Serializable
    data class Episode(
//        @SerialName("added")
//        val added: String?,
        @SerialName("container_extension")
        val containerExtension: String?,
//        @SerialName("custom_sid")
//        val customSid: String?,
//        @SerialName("direct_source")
//        val directSource: String?,
        @SerialName("episode_num")
        val episodeNum: String?,
        @SerialName("id")
        val id: String?,
//        @SerialName("info")
//        val info: Info?,
//        @SerialName("season")
//        val season: String?,
        @SerialName("title")
        val title: String?
    ) {
        @Serializable
        data class Info(
            @SerialName("audio")
            val audio: Audio?,
            @SerialName("bitrate")
            val bitrate: String?,
            @SerialName("duration")
            val duration: String?,
            @SerialName("duration_secs")
            val durationSecs: String?,
            @SerialName("video")
            val video: Video?
        ) {
            @Serializable
            data class Audio(
                @SerialName("avg_frame_rate")
                val avgFrameRate: String?,
                @SerialName("bits_per_sample")
                val bitsPerSample: String?,
                @SerialName("channels")
                val channels: String?,
                @SerialName("codec_long_name")
                val codecLongName: String?,
                @SerialName("codec_name")
                val codecName: String?,
                @SerialName("codec_tag")
                val codecTag: String?,
                @SerialName("codec_tag_string")
                val codecTagString: String?,
                @SerialName("codec_time_base")
                val codecTimeBase: String?,
                @SerialName("codec_type")
                val codecType: String?,
                @SerialName("disposition")
                val disposition: Disposition?,
                @SerialName("dmix_mode")
                val dmixMode: String?,
                @SerialName("index")
                val index: String?,
                @SerialName("loro_cmixlev")
                val loroCmixlev: String?,
                @SerialName("loro_surmixlev")
                val loroSurmixlev: String?,
                @SerialName("ltrt_cmixlev")
                val ltrtCmixlev: String?,
                @SerialName("ltrt_surmixlev")
                val ltrtSurmixlev: String?,
                @SerialName("r_frame_rate")
                val rFrameRate: String?,
                @SerialName("sample_fmt")
                val sampleFmt: String?,
                @SerialName("sample_rate")
                val sampleRate: String?,
                @SerialName("start_pts")
                val startPts: String?,
                @SerialName("start_time")
                val startTime: String?,
                @SerialName("tags")
                val tags: Map<String, String> = emptyMap(),
                @SerialName("time_base")
                val timeBase: String?
            )

            @Serializable
            data class Video(
                @SerialName("avg_frame_rate")
                val avgFrameRate: String?,
                @SerialName("bits_per_raw_sample")
                val bitsPerRawSample: String?,
                @SerialName("chroma_location")
                val chromaLocation: String?,
                @SerialName("codec_long_name")
                val codecLongName: String?,
                @SerialName("codec_name")
                val codecName: String?,
                @SerialName("codec_tag")
                val codecTag: String?,
                @SerialName("codec_tag_string")
                val codecTagString: String?,
                @SerialName("codec_time_base")
                val codecTimeBase: String?,
                @SerialName("codec_type")
                val codecType: String?,
                @SerialName("coded_height")
                val codedHeight: String?,
                @SerialName("coded_width")
                val codedWidth: String?,
                @SerialName("display_aspect_ratio")
                val displayAspectRatio: String?,
                @SerialName("disposition")
                val disposition: Disposition?,
                @SerialName("field_order")
                val fieldOrder: String?,
                @SerialName("has_b_frames")
                val hasBFrames: String?,
                @SerialName("height")
                val height: String?,
                @SerialName("index")
                val index: String?,
                @SerialName("is_avc")
                val isAvc: Boolean = false,
                @SerialName("level")
                val level: String?,
                @SerialName("nal_length_size")
                val nalLengthSize: String?,
                @SerialName("pix_fmt")
                val pixFmt: String?,
                @SerialName("profile")
                val profile: String?,
                @SerialName("r_frame_rate")
                val rFrameRate: String?,
                @SerialName("refs")
                val refs: String?,
                @SerialName("sample_aspect_ratio")
                val sampleAspectRatio: String?,
                @SerialName("start_pts")
                val startPts: String?,
                @SerialName("start_time")
                val startTime: String?,
                @SerialName("tags")
                val tags: Map<String, String> = emptyMap(),
                @SerialName("time_base")
                val timeBase: String?,
                @SerialName("width")
                val width: String?
            )

            @Serializable
            data class Disposition(
                @SerialName("attached_pic")
                val attachedPic: String?,
                @SerialName("clean_effects")
                val cleanEffects: String?,
                @SerialName("comment")
                val comment: String?,
                @SerialName("default")
                val default: String?,
                @SerialName("dub")
                val dub: String?,
                @SerialName("forced")
                val forced: String?,
                @SerialName("hearing_impaired")
                val hearingImpaired: String?,
                @SerialName("karaoke")
                val karaoke: String?,
                @SerialName("lyrics")
                val lyrics: String?,
                @SerialName("original")
                val original: String?,
                @SerialName("timed_thumbnails")
                val timedThumbnails: String?,
                @SerialName("visual_impaired")
                val visualImpaired: String?
            )
        }
    }

    @Serializable
    data class Info(
        @SerialName("backdrop_path")
        val backdropPath: List<String> = emptyList(),
        @SerialName("cast")
        val cast: String?,
        @SerialName("category_id")
        val categoryId: String?,
        @SerialName("cover")
        val cover: String?,
        @SerialName("director")
        val director: String?,
        @SerialName("episode_run_time")
        val episodeRunTime: String?,
        @SerialName("genre")
        val genre: String?,
        @SerialName("last_modified")
        val lastModified: String?,
        @SerialName("name")
        val name: String?,
        @SerialName("plot")
        val plot: String?,
        @SerialName("rating")
        val rating: String?,
        @SerialName("rating_5based")
        val rating5based: String?,
        @SerialName("releaseDate")
        val releaseDate: String?,
        @SerialName("youtube_trailer")
        val youtubeTrailer: String?
    )
}