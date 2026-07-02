package com.m3u.business.foryou

import androidx.compose.runtime.Immutable
import com.m3u.core.unit.DataUnit
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist

@Immutable
class Recommend(
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

    @Immutable
    data class CwSpec(
        val channel: Channel,
        val position: Long
    ) : Spec

    @Immutable
    data class NewRelease(
        val name: String,
        val description: String,
        val downloadCount: Int,
        val size: ClosedRange<DataUnit>,
        val url: String,
    ): Spec

    companion object {
        fun discoverSpecs(
            playlists: Map<Playlist, Int>,
            limit: Int
        ): List<DiscoverSpec> {
            return playlists
                .entries
                .asSequence()
                .filter { (playlist, count) ->
                    count > 0 && playlist.pinnedCategories.isNotEmpty()
                }
                .flatMap { (playlist, _) ->
                    playlist.pinnedCategories
                        .filterNot { it in playlist.hiddenCategories }
                        .map { category ->
                            DiscoverSpec(
                                playlist = playlist,
                                category = category
                            )
                        }
                }
                .take(limit)
                .toList()
        }
    }
}
