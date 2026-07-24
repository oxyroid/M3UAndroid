package com.m3u.data.service.internal

import com.m3u.data.repository.provider.ProviderPlaybackSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderPlaybackSessionStateTest {
    @Test
    fun staleResolveIsRejectedWithoutReplacingCurrentGenerationSession() {
        val state = ProviderPlaybackSessionState()
        val staleGeneration = state.beginGeneration()
        val currentGeneration = state.beginGeneration()
        val currentSession = session("current")

        assertTrue(
            state.attach(
                generation = currentGeneration.value,
                session = currentSession,
            ).accepted
        )

        val staleSession = session("stale")
        val staleAttachment = state.attach(
            generation = staleGeneration.value,
            session = staleSession,
        )

        assertFalse(staleAttachment.accepted)
        assertEquals(staleSession, staleAttachment.sessionToClose)
        assertNull(state.detach(staleGeneration.value))
        assertEquals(currentSession, state.detach(currentGeneration.value))
    }

    @Test
    fun sessionCapturedBeforeAsyncReleaseCannotDetachLaterPlayback() {
        val state = ProviderPlaybackSessionState()
        val firstGeneration = state.beginGeneration()
        val firstSession = session("first")
        state.attach(firstGeneration.value, firstSession)

        val releaseGeneration = state.beginGeneration()
        val nextGeneration = state.beginGeneration()
        val nextSession = session("next")
        state.attach(nextGeneration.value, nextSession)

        assertEquals(firstSession, releaseGeneration.detachedSession)
        assertNull(state.detach(firstGeneration.value))
        assertEquals(nextSession, state.detach(nextGeneration.value))
    }

    @Test
    fun rapidPlayTransitionsReturnEveryDisplacedSessionForCleanup() {
        val state = ProviderPlaybackSessionState()
        val firstGeneration = state.beginGeneration()
        val firstSession = session("first")
        state.attach(firstGeneration.value, firstSession)

        val secondGeneration = state.beginGeneration()
        val secondSession = session("second")
        state.attach(secondGeneration.value, secondSession)

        val thirdGeneration = state.beginGeneration()

        assertEquals(firstSession, secondGeneration.detachedSession)
        assertEquals(secondSession, thirdGeneration.detachedSession)
        assertTrue(state.isCurrent(thirdGeneration.value))
    }

    @Test
    fun duplicateAttachmentReturnsDisplacedSessionInsteadOfLeakingIt() {
        val state = ProviderPlaybackSessionState()
        val generation = state.beginGeneration()
        val firstSession = session("first")
        val secondSession = session("second")
        state.attach(generation.value, firstSession)

        val replacement = state.attach(generation.value, secondSession)

        assertTrue(replacement.accepted)
        assertEquals(firstSession, replacement.sessionToClose)
        assertEquals(secondSession, state.detach(generation.value))
    }

    @Test
    fun delayedReconnectCannotSupersedeNewUserPlayback() {
        val state = ProviderPlaybackSessionState()
        val endedGeneration = state.beginGeneration()
        val userGeneration = state.beginGeneration()

        val delayedReconnect = state.beginGenerationIfCurrent(endedGeneration.value)

        assertNull(delayedReconnect)
        assertTrue(state.isCurrent(userGeneration.value))
    }

    private fun session(id: String) = ProviderPlaybackSession(
        id = id,
        accountId = "account",
        providerId = "provider",
        itemId = "item-$id",
        mediaSourceId = null,
        sourceType = "live",
        playSessionId = "remote-$id",
        liveStreamId = null,
    )
}
