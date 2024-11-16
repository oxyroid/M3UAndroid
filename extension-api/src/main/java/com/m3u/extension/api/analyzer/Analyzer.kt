package com.m3u.extension.api.analyzer

sealed interface Analyzer {
    val name: String
    val description: String
}