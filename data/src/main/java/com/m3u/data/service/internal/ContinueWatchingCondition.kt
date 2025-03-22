package com.m3u.data.service.internal

import androidx.media3.common.Player
import kotlin.time.Duration.Companion.seconds

internal interface ContinueWatchingCondition<P: Player> {
    fun isStoringSupported(player: P): Boolean
    fun isRestoringSupported(player: P): Boolean
    fun isResettingSupported(player: P): Boolean
    companion object {
        @Suppress("UNCHECKED_CAST")
        inline fun <reified P: Player> getInstance(): ContinueWatchingCondition<P> =
            when(P::class) {
                Player::class -> CommonContinueWatchingCondition as ContinueWatchingCondition<P>
                else -> throw IllegalArgumentException("Unsupported player type: ${P::class}")
            }
    }
}

internal object CommonContinueWatchingCondition: ContinueWatchingCondition<Player> {
    override fun isStoringSupported(player: Player): Boolean {
        if (!isPlayerSupported(player)) return false
//        if (player.contentDuration <= 15.seconds.inWholeMilliseconds) return false
        return true
    }

    override fun isRestoringSupported(player: Player): Boolean {
        if (!player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) return false
        return true
    }

    override fun isResettingSupported(player: Player): Boolean {
        if (!isPlayerSupported(player)) return true
        val duration = player.contentDuration.toFloat()
        val position = player.contentPosition
        if (duration <= 15.seconds.inWholeMilliseconds) return true
        val remain = duration - position
        if (remain / duration <= 0.1f || remain < 5.seconds.inWholeMilliseconds) return true
        return false
    }

    private fun isPlayerSupported(player: Player): Boolean {
        if (!player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) return false
        if (player.isCurrentMediaItemDynamic || !player.isCurrentMediaItemSeekable) return false
        return true
    }
}