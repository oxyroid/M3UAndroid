package com.m3u.features.foryou.components.recommend

import androidx.compose.runtime.Immutable
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Channel

@Immutable
internal class Recommend(
    private val specs: List<Spec> = emptyList()
) : AbstractList<Recommend.Spec>() {
    override val size: Int get() = specs.size

    override fun get(index: Int): Spec = specs[index]

    @Immutable
    sealed interface Spec

    @Immutable
    data class DiscoverSpec(
        val playlist: Playlist,
        val category: String
    ) : Spec

    @Immutable
    data class UnseenSpec(
        val channel: Channel
    ) : Spec
}
