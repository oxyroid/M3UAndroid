package com.m3u.testing

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.work.WorkManager
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.worker.playlistWorkTag
import com.m3u.i18n.R.string
import com.m3u.smartphone.DebugExtensionPlatformEntryPoint
import com.m3u.smartphone.MainActivity
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlaylistManagementFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val playlistRepository: PlaylistRepository by lazy {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            DebugExtensionPlatformEntryPoint::class.java,
        ).playlistRepository()
    }

    @Test
    fun existingPlaylistRowOpensItsConfigurationScreen() {
        val fixture = createM3uFixture("existing")
        val playlistUrl = Uri.fromFile(fixture).toString()

        try {
            removePlaylistIfPresent(playlistUrl)
            runBlocking {
                playlistRepository.m3uOrThrow(
                    title = EXISTING_PLAYLIST_TITLE,
                    url = playlistUrl,
                )
            }

            openPlaylistManagementOverview()
            val playlistItemTag =
                "playlist-management-item:${playlistWorkTag(playlistUrl)}"
            waitUntilTagExists(playlistItemTag)
            composeRule.onNodeWithTag(playlistItemTag).run {
                performScrollTo()
                assertHasClickAction()
                assertTextContains(
                    EXISTING_PLAYLIST_TITLE,
                    substring = false,
                    ignoreCase = false,
                )
                performClick()
            }

            waitUntilTagExists(PLAYLIST_CONFIGURATION_TAG)
            composeRule.onNodeWithTag(PLAYLIST_CONFIGURATION_TAG)
                .assertIsDisplayed()
            composeRule.onNodeWithTag(PLAYLIST_CONFIGURATION_TITLE_TAG)
                .assertTextContains(
                    EXISTING_PLAYLIST_TITLE,
                    substring = false,
                    ignoreCase = false,
                )
            assertTrue(
                "Playlist configuration must replace the global search chrome",
                composeRule.onAllNodes(
                    hasText(
                        context.getString(string.ui_search_placeholder),
                        substring = false,
                        ignoreCase = true,
                    )
                ).fetchSemanticsNodes().isEmpty(),
            )

            val backAction = hasContentDescription(
                context.getString(string.ui_cd_top_bar_on_back_pressed),
                substring = false,
                ignoreCase = true,
            ) and hasClickAction()
            composeRule.onNode(backAction)
                .assertIsDisplayed()
                .performClick()
            waitUntilTagGone(PLAYLIST_CONFIGURATION_TAG)
            waitUntilTagExists(OVERVIEW_TAG)
        } finally {
            cleanupPlaylistFixture(playlistUrl, fixture)
        }
    }

    @Test
    fun blankConfigurationTitleCannotBeSaved() {
        val fixture = createM3uFixture("blank-title")
        val playlistUrl = Uri.fromFile(fixture).toString()

        try {
            removePlaylistIfPresent(playlistUrl)
            runBlocking {
                playlistRepository.m3uOrThrow(
                    title = TITLE_VALIDATION_PLAYLIST_TITLE,
                    url = playlistUrl,
                )
            }

            openPlaylistManagementOverview()
            val playlistItemTag =
                "playlist-management-item:${playlistWorkTag(playlistUrl)}"
            waitUntilTagExists(playlistItemTag)
            composeRule.onNodeWithTag(playlistItemTag)
                .performScrollTo()
                .performClick()
            waitUntilTagExists(PLAYLIST_CONFIGURATION_TAG)

            composeRule.onNodeWithTag(PLAYLIST_CONFIGURATION_TITLE_TAG)
                .performTextClearance()
            assertEditorError(string.feat_setting_error_empty_title)
            composeRule.onNodeWithTag(PLAYLIST_CONFIGURATION_SAVE_TAG)
                .performScrollTo()
                .assertIsNotEnabled()
            assertEquals(
                TITLE_VALIDATION_PLAYLIST_TITLE,
                runBlocking { playlistRepository.get(playlistUrl) }?.title,
            )
        } finally {
            cleanupPlaylistFixture(playlistUrl, fixture)
        }
    }

    @Test
    fun emptyM3uSubmissionShowsErrorsAndStaysOnEditor() {
        openM3uEditor()
        editorField(string.feat_setting_placeholder_title)
            .performTextClearance()
        editorField(string.feat_setting_placeholder_url)
            .performTextClearance()

        composeRule.onNodeWithTag(SUBMIT_ACTION_TAG).run {
            performScrollTo()
            assertHasClickAction()
            performClick()
        }

        waitUntilTagExists(M3U_EDITOR_TAG)
        composeRule.onNodeWithTag(M3U_EDITOR_TAG).assertIsDisplayed()
        assertEditorError(string.feat_setting_error_empty_title)
        assertEditorError(string.feat_setting_error_blank_url)
        assertTrue(
            "An invalid M3U submission must not leave the editor",
            !tagExists(OVERVIEW_TAG) && !tagExists(SOURCE_PICKER_TAG),
        )
    }

    @Test
    fun reopeningM3uEditorStartsWithAFreshDraft() {
        openM3uEditor()
        editorField(string.feat_setting_placeholder_title)
            .performTextReplacement("Discarded draft")
        editorField(string.feat_setting_placeholder_url)
            .performTextReplacement("https://example.invalid/discarded.m3u")

        hideIme()
        device.pressBack()
        waitUntilTagExists(SOURCE_PICKER_TAG)
        composeRule.onNodeWithTag(M3U_SOURCE_TAG)
            .performScrollTo()
            .performClick()
        waitUntilTagExists(M3U_EDITOR_TAG)

        editorField(string.feat_setting_placeholder_title)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText,
                    AnnotatedString(""),
                )
            )
        editorField(string.feat_setting_placeholder_url)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText,
                    AnnotatedString(""),
                )
            )
    }

    @Test
    fun acceptedM3uSubmissionReturnsToPlaylistManagementOverview() {
        grantNotificationPermissionIfNeeded()
        val fixture = createM3uFixture("accepted")
        val playlistUrl = Uri.fromFile(fixture).toString()

        try {
            cancelPlaylistWork(playlistUrl)
            removePlaylistIfPresent(playlistUrl)
            openM3uEditor()
            editorField(string.feat_setting_placeholder_title)
                .performTextReplacement(ACCEPTED_PLAYLIST_TITLE)
            editorField(string.feat_setting_placeholder_url)
                .performTextReplacement(playlistUrl)

            composeRule.onNodeWithTag(SUBMIT_ACTION_TAG).run {
                performScrollTo()
                assertHasClickAction()
                performClick()
            }

            waitForPlaylistWorkRegistration(playlistUrl)
            waitUntilTagExists(OVERVIEW_TAG)
            waitUntilTagGone(M3U_EDITOR_TAG)
            waitUntilTagGone(SOURCE_PICKER_TAG)
            composeRule.onNodeWithTag(OVERVIEW_TAG).assertIsDisplayed()
            waitUntilMatcherExists(
                hasText(
                    ACCEPTED_PLAYLIST_TITLE,
                    substring = false,
                    ignoreCase = false,
                ) or hasTestTag(UPDATE_IN_PROGRESS_TAG)
            )
        } finally {
            cleanupPlaylistFixture(playlistUrl, fixture)
        }
    }

    @Test
    fun removingPlaylistRequiresConfirmationAndReturnsToManagement() {
        val fixture = createM3uFixture("remove")
        val playlistUrl = Uri.fromFile(fixture).toString()

        try {
            removePlaylistIfPresent(playlistUrl)
            runBlocking {
                playlistRepository.m3uOrThrow(
                    title = REMOVABLE_PLAYLIST_TITLE,
                    url = playlistUrl,
                )
            }

            openPlaylistManagementOverview()
            val playlistItemTag =
                "playlist-management-item:${playlistWorkTag(playlistUrl)}"
            waitUntilTagExists(playlistItemTag)
            composeRule.onNodeWithTag(playlistItemTag)
                .performScrollTo()
                .performClick()
            waitUntilTagExists(PLAYLIST_CONFIGURATION_TAG)

            composeRule.onNodeWithTag(REMOVE_PLAYLIST_TAG).run {
                performScrollTo()
                assertHasClickAction()
                performClick()
            }
            waitUntilTagExists(REMOVE_PLAYLIST_DIALOG_TAG)
            assertTrue(
                "Opening the confirmation dialog must not remove the playlist",
                runBlocking { playlistRepository.get(playlistUrl) } != null,
            )

            composeRule.onNodeWithTag(REMOVE_PLAYLIST_CONFIRM_TAG)
                .assertHasClickAction()
                .performClick()

            waitUntilTagGone(PLAYLIST_CONFIGURATION_TAG)
            waitUntilTagExists(OVERVIEW_TAG)
            composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
                runBlocking { playlistRepository.get(playlistUrl) } == null
            }
            assertTrue(
                "The removed playlist must disappear from management",
                !tagExists(playlistItemTag),
            )
        } finally {
            cleanupPlaylistFixture(playlistUrl, fixture)
        }
    }

    @Test
    fun wideTabletSettingsListReturnsPlaylistEditorToManagementRoot() {
        val configuration = composeRule.activity.resources.configuration
        assertEquals(
            MATRIX_CASE_WIDE_LTR,
            requestedAccessibilityMatrixCase(),
        )
        assertEquals(
            "The tablet settings-list test must be LTR",
            View.LAYOUT_DIRECTION_LTR,
            configuration.layoutDirection,
        )
        assertTrue(
            "The settings list must remain visible beside the detail pane; " +
                "actual width=${configuration.screenWidthDp}dp",
            configuration.screenWidthDp >= WIDE_LIST_DETAIL_MINIMUM_DP,
        )

        openM3uEditor()
        composeRule.onNode(playlistManagementEntryMatcher()).run {
            assertIsDisplayed()
            assertIsSelected()
            performClick()
        }

        waitUntilTagExists(OVERVIEW_TAG)
        waitUntilTagGone(M3U_EDITOR_TAG)
        waitUntilTagGone(SOURCE_PICKER_TAG)
        composeRule.onNodeWithTag(OVERVIEW_TAG).assertIsDisplayed()
    }

    @Test
    fun wideTabletKeepsPlaylistConfigurationInsideSettingsContext() {
        val configuration = composeRule.activity.resources.configuration
        assertEquals(MATRIX_CASE_WIDE_LTR, requestedAccessibilityMatrixCase())
        assertTrue(configuration.screenWidthDp >= WIDE_LIST_DETAIL_MINIMUM_DP)
        val fixture = createM3uFixture("wide-configuration")
        val playlistUrl = Uri.fromFile(fixture).toString()

        try {
            removePlaylistIfPresent(playlistUrl)
            runBlocking {
                playlistRepository.m3uOrThrow(
                    title = WIDE_PLAYLIST_TITLE,
                    url = playlistUrl,
                )
            }

            openPlaylistManagementOverview()
            val playlistItemTag =
                "playlist-management-item:${playlistWorkTag(playlistUrl)}"
            waitUntilTagExists(playlistItemTag)
            composeRule.onNodeWithTag(playlistItemTag)
                .performScrollTo()
                .performClick()
            waitUntilTagExists(PLAYLIST_CONFIGURATION_TAG)

            composeRule.onNode(playlistManagementEntryMatcher())
                .assertIsDisplayed()
                .assertIsSelected()
                .performClick()
            waitUntilTagGone(PLAYLIST_CONFIGURATION_TAG)
            waitUntilTagExists(OVERVIEW_TAG)
        } finally {
            cleanupPlaylistFixture(playlistUrl, fixture)
        }
    }

    private fun openPlaylistManagementOverview() {
        if (tagExists(OVERVIEW_TAG)) return

        val playlistManagement = playlistManagementEntryMatcher()
        val settingsDestination = hasContentDescription(
            context.getString(string.ui_destination_setting),
            substring = false,
            ignoreCase = true,
        ) and hasClickAction()
        waitUntilMatcherExists(playlistManagement or settingsDestination)
        if (composeRule.onAllNodes(playlistManagement).fetchSemanticsNodes().isEmpty()) {
            composeRule.onNode(settingsDestination).performClick()
        }
        waitUntilMatcherExists(playlistManagement)
        composeRule.onNode(playlistManagement).performClick()
        waitUntilTagExists(OVERVIEW_TAG)
    }

    private fun openM3uEditor() {
        openPlaylistManagementOverview()
        composeRule.onNodeWithTag(ADD_ACTION_TAG).run {
            performScrollTo()
            performClick()
        }
        waitUntilTagExists(SOURCE_PICKER_TAG)
        composeRule.onNodeWithTag(M3U_SOURCE_TAG).run {
            performScrollTo()
            assertHasClickAction()
            performClick()
        }
        waitUntilTagExists(M3U_EDITOR_TAG)
    }

    private fun editorField(@StringRes labelResource: Int): SemanticsNodeInteraction {
        val matcher = hasSetTextAction() and hasText(
            context.getString(labelResource),
            substring = false,
            ignoreCase = true,
        )
        waitUntilMatcherExists(matcher)
        return composeRule.onNode(matcher)
    }

    private fun assertEditorError(@StringRes errorResource: Int) {
        val matcher = hasText(
            context.getString(errorResource),
            substring = false,
            ignoreCase = false,
        )
        waitUntilMatcherExists(matcher, useUnmergedTree = true)
        composeRule.onNode(matcher, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun playlistManagementEntryMatcher(): SemanticsMatcher =
        hasText(
            context.getString(string.feat_setting_playlist_management),
            substring = false,
            ignoreCase = true,
        ) and hasClickAction()

    private fun createM3uFixture(suffix: String): File =
        File(
            context.cacheDir,
            "playlist-management-$suffix-${System.nanoTime()}.m3u",
        ).apply {
            writeText(
                """
                #EXTM3U
                #EXTINF:-1 tvg-id="playlist.management.test",Test channel
                https://example.invalid/testing/stream.m3u8
                """.trimIndent() + "\n"
            )
        }

    private fun grantNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    private fun hideIme() {
        fun imeBottom(): Int {
            var bottom = 0
            composeRule.runOnIdle {
                bottom = ViewCompat.getRootWindowInsets(
                    composeRule.activity.window.decorView
                )
                    ?.getInsets(WindowInsetsCompat.Type.ime())
                    ?.bottom
                    ?: 0
            }
            return bottom
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            WindowCompat.getInsetsController(
                composeRule.activity.window,
                composeRule.activity.window.decorView,
            ).hide(WindowInsetsCompat.Type.ime())
        }
        val deadlineMillis =
            SystemClock.uptimeMillis() + IME_DISMISS_TIMEOUT_MILLIS
        var stableSamples = 0
        do {
            stableSamples = if (imeBottom() == 0) {
                stableSamples + 1
            } else {
                0
            }
            if (stableSamples >= IME_HIDDEN_STABLE_SAMPLE_COUNT) return
            SystemClock.sleep(WORK_QUIESCENCE_POLL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadlineMillis)
        throw AssertionError("IME remained visible before navigating back")
    }

    private fun waitForPlaylistWorkRegistration(playlistUrl: String) {
        val workManager = WorkManager.getInstance(context)
        val workTag = playlistWorkTag(playlistUrl)
        val deadlineNanos = System.nanoTime() +
            TimeUnit.SECONDS.toNanos(WORK_QUIESCENCE_TIMEOUT_SECONDS)
        do {
            val workRegistered = workManager
                .getWorkInfosByTag(workTag)
                .get(WORK_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .isNotEmpty()
            if (workRegistered) return
            Thread.sleep(WORK_QUIESCENCE_POLL_MILLIS)
        } while (System.nanoTime() < deadlineNanos)
        throw AssertionError("Playlist work was not registered: $workTag")
    }

    private fun cancelPlaylistWork(playlistUrl: String) {
        val workManager = WorkManager.getInstance(context)
        val workTag = playlistWorkTag(playlistUrl)
        workManager
            .cancelAllWorkByTag(workTag)
            .result
            .get(WORK_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        val deadlineNanos = System.nanoTime() +
            TimeUnit.SECONDS.toNanos(WORK_QUIESCENCE_TIMEOUT_SECONDS)
        do {
            val allWorkFinished = workManager
                .getWorkInfosByTag(workTag)
                .get(WORK_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .all { workInfo -> workInfo.state.isFinished }
            if (allWorkFinished) return
            Thread.sleep(WORK_QUIESCENCE_POLL_MILLIS)
        } while (System.nanoTime() < deadlineNanos)

        throw AssertionError(
            "Playlist work did not reach a terminal state during test cleanup: $workTag"
        )
    }

    private fun removePlaylistIfPresent(playlistUrl: String) {
        runBlocking {
            playlistRepository.get(playlistUrl)?.let {
                playlistRepository.unsubscribe(playlistUrl)
            }
        }
    }

    private fun cleanupPlaylistFixture(
        playlistUrl: String,
        fixture: File,
    ) {
        val failures = buildList {
            runCatching { cancelPlaylistWork(playlistUrl) }
                .exceptionOrNull()
                ?.let(::add)
            runCatching { removePlaylistIfPresent(playlistUrl) }
                .exceptionOrNull()
                ?.let(::add)
            if (fixture.exists() && !fixture.delete()) {
                add(IllegalStateException("Unable to delete M3U fixture: $fixture"))
            }
        }
        if (failures.isNotEmpty()) {
            throw AssertionError(
                "Failed to clean playlist-management test state",
                failures.first(),
            ).also { error ->
                failures.drop(1).forEach(error::addSuppressed)
            }
        }
    }

    private fun requestedAccessibilityMatrixCase(): String =
        InstrumentationRegistry.getArguments()
            .getString(ARG_ACCESSIBILITY_MATRIX_CASE)
            ?: error(
                "Missing required instrumentation argument " +
                    "$ARG_ACCESSIBILITY_MATRIX_CASE.",
            )

    private fun waitUntilTagExists(tag: String) {
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) { tagExists(tag) }
    }

    private fun waitUntilTagGone(tag: String) {
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) { !tagExists(tag) }
    }

    private fun waitUntilMatcherExists(
        matcher: SemanticsMatcher,
        useUnmergedTree: Boolean = false,
    ) {
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(
                matcher,
                useUnmergedTree = useUnmergedTree,
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tagExists(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val UI_TIMEOUT_MILLIS = 15_000L
        const val IME_DISMISS_TIMEOUT_MILLIS = 5_000L
        const val IME_HIDDEN_STABLE_SAMPLE_COUNT = 3
        const val WORK_OPERATION_TIMEOUT_SECONDS = 5L
        const val WORK_QUIESCENCE_TIMEOUT_SECONDS = 10L
        const val WORK_QUIESCENCE_POLL_MILLIS = 50L
        const val ARG_ACCESSIBILITY_MATRIX_CASE = "accessibilityMatrixCase"
        const val MATRIX_CASE_WIDE_LTR = "wide-ltr"
        const val WIDE_LIST_DETAIL_MINIMUM_DP = 840

        const val EXISTING_PLAYLIST_TITLE = "Existing playlist navigation"
        const val ACCEPTED_PLAYLIST_TITLE = "Accepted playlist navigation"
        const val REMOVABLE_PLAYLIST_TITLE = "Removable playlist navigation"
        const val TITLE_VALIDATION_PLAYLIST_TITLE = "Protected playlist title"
        const val WIDE_PLAYLIST_TITLE = "Wide playlist navigation"
        const val OVERVIEW_TAG = "playlist-management-overview"
        const val ADD_ACTION_TAG = "playlist-add-action"
        const val SOURCE_PICKER_TAG = "playlist-source-picker"
        const val M3U_SOURCE_TAG = "playlist-source:data-source:m3u"
        const val M3U_EDITOR_TAG = "playlist-editor:data-source:m3u"
        const val SUBMIT_ACTION_TAG = "subscription-submit-action"
        const val PLAYLIST_CONFIGURATION_TAG = "playlist-configuration"
        const val PLAYLIST_CONFIGURATION_TITLE_TAG =
            "playlist-configuration-title"
        const val PLAYLIST_CONFIGURATION_SAVE_TAG =
            "playlist-configuration-save"
        const val UPDATE_IN_PROGRESS_TAG =
            "playlist-management-update-in-progress"
        const val REMOVE_PLAYLIST_TAG = "playlist-configuration-remove"
        const val REMOVE_PLAYLIST_DIALOG_TAG =
            "playlist-configuration-remove-dialog"
        const val REMOVE_PLAYLIST_CONFIRM_TAG =
            "playlist-configuration-remove-confirm"
    }
}
