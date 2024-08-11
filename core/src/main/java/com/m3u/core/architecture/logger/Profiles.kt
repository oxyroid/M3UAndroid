package com.m3u.core.architecture.logger

import com.m3u.core.wrapper.Message

object Profiles {
    val VIEWMODEL_FAVOURITE = Profile("viewmodel-favourite")
    val VIEWMODEL_FORYOU = Profile("viewmodel-foryou")
    val VIEWMODEL_PLAYLIST = Profile("viewmodel-playlist")
    val VIEWMODEL_SETTING = Profile("viewmodel-setting")
    val VIEWMODEL_CHANNEL = Profile("viewmodel-channel")
    val VIEWMODEL_PLAYLIST_CONFIGURATION = Profile("viewmodel-playlist-configuration", Message.LEVEL_INFO)

    val REPOS_PLAYLIST = Profile("repos-playlist")
    val REPOS_CHANNEL = Profile("repos-channel")
    val REPOS_PROGRAMME = Profile("repos-programme")
    val REPOS_LEANBACK = Profile("repos-tv")
    val REPOS_MEDIA = Profile("repos-media")
    val REPOS_OTHER = Profile("repos-other")

    val PARSER_M3U = Profile("parser-m3u")
    val PARSER_XTREAM = Profile("parser-xtream")
    val PARSER_EPG = Profile("parser-epg")

    val SERVICE_PLAYER = Profile("service-player")
    val SERVICE_NSD = Profile("service-nsd")

    val WORKER_BACKUP = Profile("worker-backup")
    val WORKER_RESTORE = Profile("worker-restore")
    val WORKER_SUBSCRIPTION = Profile("worker-subscription")
}