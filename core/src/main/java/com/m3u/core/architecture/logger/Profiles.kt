package com.m3u.core.architecture.logger

object Profiles {
    val VIEWMODEL_FAVOURITE = Profile("viewmodel-favourite", false)
    val VIEWMODEL_FORYOU = Profile("viewmodel-foryou", false)
    val VIEWMODEL_PLAYLIST = Profile("viewmodel-playlist", false)
    val VIEWMODEL_SETTING = Profile("viewmodel-setting", false)
    val VIEWMODEL_STREAM = Profile("viewmodel-stream", false)

    val REPOS_PLAYLIST = Profile("repos-playlist", false)
    val REPOS_STREAM = Profile("repos-stream", false)
    val REPOS_TELEVISION = Profile("repos-television", false)
    val REPOS_MEDIA = Profile("repos-media", false)

    val PARSER_M3U = Profile("parser-m3u", false)
    val PARSER_XTREAM = Profile("parser-xtream", false)
//     val PARSER_EPG = false

    val SERVICE_PLAYER = Profile("service-player", false)
    val SERVICE_NSD = Profile("service-nsd", false)

    val WORKER_BACKUP = Profile("worker-backup", false)
    val WORKER_RESTORE = Profile("worker-restore", false)
    val WORKER_SUBSCRIPTION = Profile("worker-subscription", false)
}