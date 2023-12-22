package com.m3u.features.about

import androidx.compose.runtime.Immutable
import com.m3u.data.parser.VersionCatalogParser
import com.m3u.features.about.model.Contributor

@Immutable
internal data class AboutState(
    val contributors: List<Contributor> = emptyList(),
    val libraries: List<VersionCatalogParser.Entity.Library> = emptyList()
)
