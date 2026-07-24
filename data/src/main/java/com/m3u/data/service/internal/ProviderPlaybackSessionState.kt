package com.m3u.data.service.internal

import com.m3u.data.repository.provider.ProviderPlaybackSession

/**
 * Owns the association between one logical playback generation and its provider session.
 *
 * All transitions are synchronous so callback callers can detach the exact session before
 * scheduling asynchronous cleanup. Network cleanup must happen outside this class.
 */
internal class ProviderPlaybackSessionState {
    private val lock = Any()
    private var currentGeneration = 0L
    private var activeSession: GenerationSession? = null

    fun beginGeneration(): ProviderPlaybackGeneration = synchronized(lock) {
        advanceGeneration()
    }

    fun beginGenerationIfCurrent(
        expectedGeneration: Long,
    ): ProviderPlaybackGeneration? = synchronized(lock) {
        if (currentGeneration != expectedGeneration) return@synchronized null
        advanceGeneration()
    }

    private fun advanceGeneration(): ProviderPlaybackGeneration {
        currentGeneration += 1L
        return ProviderPlaybackGeneration(
            value = currentGeneration,
            detachedSession = activeSession?.session,
        ).also {
            activeSession = null
        }
    }

    fun isCurrent(generation: Long): Boolean = synchronized(lock) {
        currentGeneration == generation
    }

    fun runIfCurrent(
        generation: Long,
        action: () -> Unit,
    ): Boolean = synchronized(lock) {
        if (currentGeneration != generation) return@synchronized false
        action()
        true
    }

    fun attach(
        generation: Long,
        session: ProviderPlaybackSession?,
    ): ProviderPlaybackSessionAttachment = synchronized(lock) {
        if (currentGeneration != generation) {
            return@synchronized ProviderPlaybackSessionAttachment(
                accepted = false,
                sessionToClose = session,
            )
        }
        val displacedSession = activeSession?.session
        activeSession = session?.let {
            GenerationSession(
                generation = generation,
                session = it,
            )
        }
        ProviderPlaybackSessionAttachment(
            accepted = true,
            sessionToClose = displacedSession,
        )
    }

    fun detach(generation: Long): ProviderPlaybackSession? = synchronized(lock) {
        val current = activeSession
            ?.takeIf { owned -> owned.generation == generation }
            ?: return@synchronized null
        activeSession = null
        current.session
    }

    private data class GenerationSession(
        val generation: Long,
        val session: ProviderPlaybackSession,
    )
}

internal data class ProviderPlaybackGeneration(
    val value: Long,
    val detachedSession: ProviderPlaybackSession?,
)

internal data class ProviderPlaybackSessionAttachment(
    val accepted: Boolean,
    val sessionToClose: ProviderPlaybackSession?,
)
