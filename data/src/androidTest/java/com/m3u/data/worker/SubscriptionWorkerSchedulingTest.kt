package com.m3u.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.impl.model.WorkSpec
import com.m3u.data.database.model.DataSource
import com.m3u.data.parser.xtream.XtreamInput
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionWorkerSchedulingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workManager = WorkManager.getInstance(context)
    private val uniqueWorkNames = mutableSetOf<String>()

    @After
    fun cancelScheduledWork() {
        uniqueWorkNames.forEach { uniqueWorkName ->
            workManager.cancelUniqueWork(uniqueWorkName).result.get(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun localM3uNeedsNoNetworkWhileRemoteM3uDoes() {
        val nonce = UUID.randomUUID()
        val localUrl = "content://com.m3u.test/playlists/$nonce"
        val remoteUrl = "https://reader:private-token@example.test/$nonce/list.m3u"
        val localWorkName = m3uSubscriptionWorkName(localUrl)
        val remoteWorkName = m3uSubscriptionWorkName(remoteUrl)
        uniqueWorkNames += localWorkName
        uniqueWorkNames += remoteWorkName

        val localWorkId = SubscriptionWorker.m3u(
            workManager = workManager,
            title = "Local",
            url = localUrl,
            requireExistingPlaylist = false,
        )
        val remoteWorkId = SubscriptionWorker.m3u(
            workManager = workManager,
            title = "Remote",
            url = remoteUrl,
            requireExistingPlaylist = true,
        )

        val local = workSpecFor(localWorkName)
        val remote = workSpecFor(remoteWorkName)

        assertEquals(localWorkId.toString(), local.id)
        assertEquals(remoteWorkId.toString(), remote.id)
        assertEquals(NetworkType.NOT_REQUIRED, local.constraints.requiredNetworkType)
        assertEquals(NetworkType.CONNECTED, remote.constraints.requiredNetworkType)
        assertFalse(local.input.getBoolean(REQUIRE_EXISTING_INPUT, true))
        assertTrue(remote.input.getBoolean(REQUIRE_EXISTING_INPUT, false))
        assertSafeIdentity(localWorkName, localUrl, "playlists", nonce.toString())
        assertSafeIdentity(
            remoteWorkName,
            remoteUrl,
            "reader",
            "private-token",
            nonce.toString(),
        )
        assertSafeTags(local, localUrl, nonce.toString())
        assertSafeTags(
            remote,
            remoteUrl,
            "reader",
            "private-token",
            nonce.toString(),
        )
        assertEquals(emptyList<WorkInfo>(), workInfosForUniqueWork(localUrl))
        assertEquals(emptyList<WorkInfo>(), workInfosForUniqueWork(remoteUrl))
    }

    @Test
    fun epgAndXtreamUseHashedReplacementIdentitiesWithoutLeakingCredentials() {
        val nonce = UUID.randomUUID()
        val epgUrl =
            "https://guide-user:guide-secret@example.test/$nonce/guide.xml?token=epg-token"
        val epgWorkName = epgSubscriptionWorkName(epgUrl)
        val basicUrl = "https://xtream.example.test/$nonce"
        val username = "subscriber-$nonce"
        val password = "password-$nonce"
        val playlistUrl = XtreamInput.encodeToPlaylistUrl(
            XtreamInput(
                basicUrl = basicUrl,
                username = username,
                password = password,
                type = DataSource.Xtream.TYPE_LIVE,
            )
        )
        val xtreamWorkName = hashedWorkTag(
            namespace = "subscription-xtream",
            value = "$basicUrl\u0000$username",
        )
        uniqueWorkNames += epgWorkName
        uniqueWorkNames += xtreamWorkName

        val epgWorkId = SubscriptionWorker.epg(
            workManager = workManager,
            playlistUrl = epgUrl,
            ignoreCache = true,
        )
        val xtreamWorkId = SubscriptionWorker.xtream(
            workManager = workManager,
            title = "Private account",
            url = playlistUrl,
            basicUrl = basicUrl,
            username = username,
            password = password,
            requireExistingPlaylist = true,
        )

        val epg = workSpecFor(epgWorkName)
        val xtream = workSpecFor(xtreamWorkName)

        assertEquals(epgWorkId.toString(), epg.id)
        assertEquals(xtreamWorkId.toString(), xtream.id)
        assertEquals(NetworkType.CONNECTED, epg.constraints.requiredNetworkType)
        assertEquals(NetworkType.CONNECTED, xtream.constraints.requiredNetworkType)
        assertTrue(epg.input.getBoolean(EPG_IGNORE_CACHE_INPUT, false))
        assertTrue(xtream.input.getBoolean(REQUIRE_EXISTING_INPUT, false))
        assertSafeIdentity(
            epgWorkName,
            epgUrl,
            "guide-user",
            "guide-secret",
            "epg-token",
            nonce.toString(),
        )
        assertSafeTags(
            epg,
            epgUrl,
            "guide-user",
            "guide-secret",
            "epg-token",
            nonce.toString(),
        )
        assertSafeIdentity(
            xtreamWorkName,
            playlistUrl,
            basicUrl,
            username,
            password,
            nonce.toString(),
        )
        assertSafeTags(
            xtream,
            playlistUrl,
            basicUrl,
            username,
            password,
            nonce.toString(),
        )
        assertEquals(emptyList<WorkInfo>(), workInfosForUniqueWork(epgUrl))
        assertEquals(emptyList<WorkInfo>(), workInfosForUniqueWork(basicUrl))
        assertEquals(emptyList<WorkInfo>(), workInfosForUniqueWork(playlistUrl))
    }

    @Test
    fun replacementIdentityIsStableButDifferentSourcesCannotCollide() {
        val value = "https://example.test/private/list.m3u?token=secret"

        assertEquals(
            m3uSubscriptionWorkName(value),
            m3uSubscriptionWorkName(value),
        )
        assertNotEquals(
            m3uSubscriptionWorkName(value),
            epgSubscriptionWorkName(value),
        )
        assertNotEquals(
            m3uSubscriptionWorkName(value),
            xtreamPlaylistWorkTag(value),
        )
        assertNotEquals(
            epgSubscriptionWorkName(value),
            xtreamPlaylistWorkTag(value),
        )
    }

    private fun workSpecFor(uniqueWorkName: String): WorkSpec {
        val info = workInfosForUniqueWork(uniqueWorkName).single()
        val implementation = workManager as WorkManagerImpl
        return checkNotNull(
            implementation.workDatabase.workSpecDao().getWorkSpec(info.id.toString())
        )
    }

    private fun workInfosForUniqueWork(uniqueWorkName: String): List<WorkInfo> =
        workManager
            .getWorkInfosForUniqueWork(uniqueWorkName)
            .get(5, TimeUnit.SECONDS)

    private fun assertSafeTags(
        workSpec: WorkSpec,
        vararg sensitiveValues: String,
    ) {
        val info = workManager
            .getWorkInfoById(UUID.fromString(workSpec.id))
            .get(5, TimeUnit.SECONDS)
        checkNotNull(info)
        info.tags.forEach { tag ->
            sensitiveValues.forEach { sensitive ->
                assertFalse(
                    "WorkManager tag leaked sensitive input: $tag",
                    tag.contains(sensitive, ignoreCase = true),
                )
            }
        }
    }

    private fun assertSafeIdentity(
        identity: String,
        vararg sensitiveValues: String,
    ) {
        assertTrue(identity.matches(SAFE_WORK_IDENTITY))
        sensitiveValues.forEach { sensitive ->
            assertFalse(
                "Unique work identity leaked sensitive input: $identity",
                identity.contains(sensitive, ignoreCase = true),
            )
        }
    }

    private companion object {
        const val REQUIRE_EXISTING_INPUT = "require-existing-playlist"
        const val EPG_IGNORE_CACHE_INPUT = "ignore_cache"
        val SAFE_WORK_IDENTITY = Regex("[a-z0-9-]+:[0-9a-f]{64}")
    }
}
