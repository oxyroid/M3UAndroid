package com.m3u.data.parser

import java.io.InputStream

interface VersionCatalogParser : Parser<InputStream, List<VersionCatalogParser.Entity>> {
    sealed interface Entity {

        data class Version(val key: String, val value: String) : Entity

        data class Library(
            val key: String,
            val group: String,
            val name: String,
            val ref: String,
        ) : Entity

        data class Plugin(
            val key: String,
            val id: String,
            val ref: String
        ) : Entity
    }
}