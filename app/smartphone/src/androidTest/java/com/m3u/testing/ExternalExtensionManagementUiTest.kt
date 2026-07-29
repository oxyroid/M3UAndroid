package com.m3u.testing

import android.os.SystemClock
import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.accessibility.disableAccessibilityChecks
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import com.m3u.business.setting.ExtensionPluginOperation
import com.m3u.business.setting.ExtensionPluginOperationState
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.settings
import com.m3u.data.repository.extension.ExtensionNetworkOriginState
import com.m3u.extension.api.ExtensionState
import com.m3u.i18n.R.string
import com.m3u.smartphone.DebugExtensionPlatformEntryPoint
import com.m3u.smartphone.MainActivity
import com.m3u.smartphone.ui.business.setting.fragments.ExtensionPluginDetailContentState
import com.m3u.smartphone.ui.business.setting.fragments.ExtensionPluginDetailScreen
import com.m3u.smartphone.ui.business.setting.fragments.ExtensionPluginDiscoveryStatus
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ExternalExtensionManagementUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationContext

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private var externalExtensionsPreviouslyEnabled: Boolean? = null

    @Before
    fun enableExternalExtensions() {
        runBlocking {
            externalExtensionsPreviouslyEnabled = targetContext.settings.data
                .first()[PreferencesKeys.EXTERNAL_EXTENSIONS]
            targetContext.settings.edit { preferences ->
                preferences[PreferencesKeys.EXTERNAL_EXTENSIONS] = true
            }
        }
    }

    @After
    fun removeExtensionTrustAndRestoreDeveloperMode() {
        runBlocking {
            val cleanupFailure = runCatching {
                val repository = EntryPointAccessors.fromApplication(
                    targetContext,
                    DebugExtensionPlatformEntryPoint::class.java,
                ).pluginRepository()
                repository.installedPlugins()
                    .filter { plugin -> plugin.packageName == REFERENCE_PACKAGE }
                    .forEach { plugin ->
                        repository.revoke(
                            packageName = plugin.packageName,
                            serviceName = plugin.serviceName,
                        )
                    }
            }.exceptionOrNull()
            targetContext.settings.edit { preferences ->
                externalExtensionsPreviouslyEnabled?.let { enabled ->
                    preferences[PreferencesKeys.EXTERNAL_EXTENSIONS] = enabled
                } ?: preferences.remove(PreferencesKeys.EXTERNAL_EXTENSIONS)
            }
            cleanupFailure?.let { cause ->
                throw AssertionError("Failed to remove reference extension trust", cause)
            }
        }
    }

    @Test
    fun directDetailRenderingDistinguishesLookupStatesAndBusyPluginContent() {
        assertRequestedAccessibilityConfigurationIfPresent()
        val repository = EntryPointAccessors.fromApplication(
            targetContext,
            DebugExtensionPlatformEntryPoint::class.java,
        ).pluginRepository()
        val inspectedPlugin = runBlocking {
            repository.installedPlugins().single { plugin ->
                plugin.packageName == REFERENCE_PACKAGE &&
                    plugin.serviceName == REFERENCE_SERVICE
            }
        }
        assertTrue(
            "The installed reference plugin must provide an authorization token",
            inspectedPlugin.authorizationToken != null,
        )
        val plugin = inspectedPlugin.copy(
            trusted = true,
            signatureChanged = false,
            extensionId = REFERENCE_EXTENSION_ID,
            enabled = true,
            state = ExtensionState.ENABLED,
            version = LONG_REFERENCE_VERSION,
            grantedCapabilities = inspectedPlugin.requestedCapabilities,
            capabilityPermissions = inspectedPlugin.capabilityPermissions.map { permission ->
                permission.copy(granted = true)
            },
            inspectionError = null,
            installed = true,
            approvedNetworkOrigins = inspectedPlugin.networkOrigins,
            networkAccess = inspectedPlugin.networkAccess.copy(
                fixedOrigins = inspectedPlugin.networkAccess.fixedOrigins.map { origin ->
                    origin.copy(state = ExtensionNetworkOriginState.APPROVED)
                }
            ),
        )
        val detailState = mutableStateOf<ExtensionPluginDetailContentState>(
            ExtensionPluginDetailContentState.Loading
        )
        val operationState = mutableStateOf<ExtensionPluginOperationState>(
            ExtensionPluginOperationState.Idle
        )
        val retryRequested = AtomicBoolean(false)

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        ExtensionPluginDetailScreen(
                            state = detailState.value,
                            operationState = operationState.value,
                            onRetryDiscovery = { retryRequested.set(true) },
                            onOpenAuthorization = {},
                            onOpenSettings = {},
                            onDisable = {},
                            onRevoke = { _, _, _ -> },
                            onClearData = { _, _, _ -> },
                            onExportDiagnostics = {},
                        )
                    }
                }
            }
        }

        waitUntilTagExists(PLUGIN_DETAIL_LOADING_TAG)
        composeRule.onNodeWithTag(PLUGIN_DETAIL_LOADING_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithTag(PLUGIN_UNAVAILABLE_TAG).assertCountEquals(0)

        composeRule.runOnIdle {
            detailState.value = ExtensionPluginDetailContentState.Failure
        }
        waitUntilTagExists(PLUGIN_FAILURE_TAG)
        composeRule.onNodeWithTag(PLUGIN_FAILURE_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithTag(PLUGIN_UNAVAILABLE_TAG).assertCountEquals(0)
        composeRule.onNodeWithTag(PLUGIN_RETRY_TAG)
            .assertIsDisplayed()
            .assertMinimumTouchTarget()
            .performClick()
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            retryRequested.get()
        }

        composeRule.runOnIdle {
            detailState.value = ExtensionPluginDetailContentState.Missing
        }
        waitUntilTagExists(PLUGIN_UNAVAILABLE_TAG)
        composeRule.onNodeWithTag(PLUGIN_UNAVAILABLE_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithTag(PLUGIN_DETAIL_LOADING_TAG).assertCountEquals(0)

        composeRule.runOnIdle {
            detailState.value = ExtensionPluginDetailContentState.Content(
                plugin = plugin,
                discoveryStatus = ExtensionPluginDiscoveryStatus.READY,
            )
            operationState.value = ExtensionPluginOperationState.Running(
                ExtensionPluginOperation.Reauthorize(
                    packageName = REFERENCE_PACKAGE,
                    serviceName = REFERENCE_SERVICE,
                )
            )
        }
        waitUntilTagExists(pluginDetailTag())
        waitUntilTagExists(OPERATION_PROGRESS_TAG)
        composeRule.onAllNodesWithTag(PLUGIN_UNAVAILABLE_TAG).assertCountEquals(0)
        val operationDescription = composeRule.activity.getString(
            string.feat_setting_extension_operation_reauthorizing
        )
        composeRule.onNodeWithTag(OPERATION_PROGRESS_TAG)
            .assertIsDisplayed()
            .assertTextContains(operationDescription, substring = false)
            .assert(
                SemanticsMatcher("does not repeat visible text as a state description") {
                    !it.config.contains(SemanticsProperties.StateDescription)
                }
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                )
            )

        assertBusyDetailActionsAreDisabledAndDoNotOverlap()

        composeRule.runOnIdle {
            operationState.value = ExtensionPluginOperationState.Running(
                ExtensionPluginOperation.Reauthorize(
                    packageName = REFERENCE_PACKAGE,
                    serviceName = "$REFERENCE_SERVICE.Other",
                )
            )
        }
        val otherOperationDescription = composeRule.activity.getString(
            string.feat_setting_extension_operation_other_in_progress
        )
        composeRule.onNodeWithTag(OPERATION_PROGRESS_TAG)
            .assertTextContains(otherOperationDescription, substring = false)

        scrollDetailTo(TECHNICAL_IDENTITY_DISCLOSURE_TAG)
        composeRule.onNodeWithTag(TECHNICAL_IDENTITY_DISCLOSURE_TAG)
            .assertMinimumTouchTarget()
            .performClick()
        assertTechnicalIdentityValue("version", LONG_REFERENCE_VERSION)
        assertTechnicalIdentityValue("package", REFERENCE_PACKAGE)
        assertTechnicalIdentityValue("service", REFERENCE_SERVICE)
        assertTechnicalIdentityValue(
            "certificate",
            plugin.certificateSha256.chunked(16).joinToString(" "),
        )
    }

    @Test
    fun referencePluginCompletesTheVisibleManagementLifecycle() {
        assertRequestedAccessibilityConfigurationIfPresent()
        openExtensionPlugins()

        waitUntilTagExists(PLUGIN_LIST_TAG)
        composeRule.enableAccessibilityChecks()
        composeRule.onRoot().tryPerformAccessibilityChecks()
        composeRule.disableAccessibilityChecks()
        assertAdaptiveNavigation(nestedDetailVisible = true)
        waitUntilTagExists(pluginListItemTag())
        composeRule.onNodeWithTag(pluginListItemTag())
            .assertMinimumTouchTarget()
            .performClick()
        waitUntilTagExists(pluginDetailTag())
        waitUntilTagGone(PLUGIN_LIST_TAG)
        waitUntilTagExists(CAPABILITIES_DISCLOSURE_TAG)
        waitUntilTagExists(NETWORK_ORIGINS_DISCLOSURE_TAG)
        composeRule.onAllNodes(
            hasText(REFERENCE_CAPABILITY_ID, substring = true, ignoreCase = false)
        ).assertCountEquals(0)
        val referenceCapabilityName = composeRule.activity.getString(
            string.feat_setting_extension_capability_name_background_task
        )
        composeRule.onAllNodes(
            hasText(referenceCapabilityName, substring = false, ignoreCase = false)
        ).assertCountEquals(0)
        composeRule.onNodeWithTag(CAPABILITIES_DISCLOSURE_TAG)
            .assertMinimumTouchTarget()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    composeRule.activity.getString(string.ui_state_collapsed),
                )
            )
            .performClick()
        composeRule.onNodeWithTag(CAPABILITIES_DISCLOSURE_TAG)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    composeRule.activity.getString(string.ui_state_expanded),
                )
            )
        waitUntilExists(hasText(referenceCapabilityName, substring = false))
        composeRule.onAllNodes(
            hasText(REFERENCE_CAPABILITY_ID, substring = true, ignoreCase = false)
        ).assertCountEquals(0)
        composeRule.onNodeWithTag(CAPABILITIES_DISCLOSURE_TAG)
            .performClick()
        waitUntilGone(hasText(referenceCapabilityName, substring = false))
        scrollDetailTo(NETWORK_ORIGINS_DISCLOSURE_TAG)
        composeRule.onNodeWithTag(NETWORK_ORIGINS_DISCLOSURE_TAG)
            .assertMinimumTouchTarget()
            .assertDisclosureState(string.ui_state_collapsed)
            .performClick()
            .assertDisclosureState(string.ui_state_expanded)
            .performClick()
            .assertDisclosureState(string.ui_state_collapsed)
        scrollDetailTo(TECHNICAL_IDENTITY_DISCLOSURE_TAG)
        composeRule.onNodeWithTag(TECHNICAL_IDENTITY_DISCLOSURE_TAG)
            .assertMinimumTouchTarget()
            .assertDisclosureState(string.ui_state_collapsed)
            .performClick()
            .assertDisclosureState(string.ui_state_expanded)
        waitUntilExists(
            hasText(REFERENCE_PACKAGE, substring = true, ignoreCase = false)
        )
        composeRule.onNodeWithTag(TECHNICAL_IDENTITY_DISCLOSURE_TAG)
            .performClick()
            .assertDisclosureState(string.ui_state_collapsed)
        waitUntilGone(
            hasText(REFERENCE_PACKAGE, substring = true, ignoreCase = false)
        )
        scrollDetailTo(actionTag("enable"))
        waitUntilTagEnabled(actionTag("enable"))
        composeRule.onNodeWithTag(actionTag("enable"))
            .assertMinimumTouchTarget()
            .performClick()
        waitUntilTagExists(AUTHORIZATION_SCREEN_TAG)
        composeRule.onAllNodes(
            hasText(REFERENCE_PACKAGE, substring = true, ignoreCase = false)
        ).assertCountEquals(0)
        waitUntilExists(
            hasText(
                composeRule.activity.getString(
                    string.feat_setting_extension_requested_capabilities
                ),
                substring = false,
                ignoreCase = true,
            )
        )
        composeRule.onNodeWithTag(AUTHORIZATION_SCREEN_TAG)
            .performScrollToNode(hasTestTag(AUTHORIZATION_IDENTITY_DISCLOSURE_TAG))
        waitUntilTagExists(AUTHORIZATION_IDENTITY_DISCLOSURE_TAG)
        composeRule.onNodeWithTag(AUTHORIZATION_IDENTITY_DISCLOSURE_TAG)
            .assertMinimumTouchTarget()
            .assertDisclosureState(string.ui_state_collapsed)
            .performClick()
            .assertDisclosureState(string.ui_state_expanded)
        waitUntilExists(hasText(REFERENCE_PACKAGE, substring = true))
        composeRule.onNodeWithTag(AUTHORIZATION_IDENTITY_DISCLOSURE_TAG)
            .performClick()
            .assertDisclosureState(string.ui_state_collapsed)
        waitUntilGone(hasText(REFERENCE_PACKAGE, substring = true))
        scrollAuthorizationActionsIntoView()
        assertAuthorizationActionsAreUsable()
        physicallyClickAuthorization(string.feat_setting_extension_enable)
        waitUntilTagGone(AUTHORIZATION_SCREEN_TAG)
        waitUntilTagExists(pluginDetailTag())
        openSettings()
        composeRule.onNodeWithTag(choiceTag("direct"))
            .performScrollTo()
            .assertMinimumTouchTarget()
            .performClick()
        composeRule.onNodeWithTag(choiceTag("direct")).assertIsSelected()
        composeRule.onNodeWithTag(choiceTag("auto")).performScrollTo()
        assertFlowRowControlsDoNotOverlap(
            firstTag = choiceTag("direct"),
            secondTag = choiceTag("auto"),
        )
        composeRule.onNodeWithTag(API_KEY_FIELD_TAG)
            .performScrollTo()
            .performTextInput("ui-secret")
        composeRule.onNodeWithTag(API_KEY_SAVE_TAG)
            .performScrollTo()
            .assertMinimumTouchTarget()
            .performClick()
        waitUntilTagExists(API_KEY_CLEAR_TAG)
        composeRule.onNodeWithTag(API_KEY_CLEAR_TAG)
            .performScrollTo()
            .assertMinimumTouchTarget()
        composeRule.onNodeWithTag(API_KEY_FIELD_TAG)
            .performScrollTo()
            .performTextInput("ui-replacement")
        waitUntilTagExists(API_KEY_SAVE_TAG)
        composeRule.onNodeWithTag(API_KEY_SAVE_TAG).performScrollTo()
        assertFlowRowControlsDoNotOverlap(
            firstTag = API_KEY_SAVE_TAG,
            secondTag = API_KEY_CLEAR_TAG,
        )
        val notConfiguredOriginState = composeRule.activity.getString(
            string.feat_setting_extension_network_origin_state_not_configured
        )
        composeRule.onNodeWithTag(SETTINGS_SCREEN_TAG)
            .performScrollToNode(hasTestTag(API_ORIGIN_FIELD_TAG))
        waitUntilTagExists(API_ORIGIN_FIELD_TAG)
        composeRule.onNodeWithTag(API_ORIGIN_STATE_TAG, useUnmergedTree = true)
            .performScrollTo()
            .assertTextContains(notConfiguredOriginState, substring = false)
        composeRule.onNodeWithTag(API_ORIGIN_FIELD_TAG)
            .performScrollTo()
            .performTextInput(REFERENCE_SETTING_ORIGIN)
        composeRule.onNodeWithTag(API_ORIGIN_SAVE_TAG)
            .performScrollTo()
            .assertMinimumTouchTarget()
            .performClick()
        val approvedOriginState = composeRule.activity.getString(
            string.feat_setting_extension_network_origin_state_approved
        )
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            runCatching {
                composeRule.onNodeWithTag(
                    API_ORIGIN_STATE_TAG,
                    useUnmergedTree = true,
                )
                    .assertTextContains(approvedOriginState, substring = false)
            }.isSuccess
        }
        closeSettings()

        scrollDetailTo(NETWORK_ORIGINS_DISCLOSURE_TAG)
        composeRule.onNodeWithTag(NETWORK_ORIGINS_DISCLOSURE_TAG)
            .performClick()
        waitUntilTagExists(API_ORIGIN_DETAIL_TAG)
        composeRule.onNodeWithTag(API_ORIGIN_DETAIL_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        waitUntilExists(
            hasText(REFERENCE_SETTING_ORIGIN, substring = false)
        )
        composeRule.onNodeWithTag(API_ORIGIN_DETAIL_TAG).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                approvedOriginState,
            )
        )
        composeRule.onNodeWithTag(NETWORK_ORIGINS_DISCLOSURE_TAG)
            .performClick()

        scrollDetailTo(actionTag("clear-data"))
        waitUntilTagEnabled(actionTag("clear-data"))
        composeRule.onNodeWithTag(actionTag("clear-data"))
            .assertMinimumTouchTarget()
            .performClick()
        waitUntilTagExists(CLEAR_DATA_DIALOG_TAG)
        composeRule.onNodeWithTag(DATA_REMOVAL_PACKAGE_TAG)
            .assertTextContains(REFERENCE_PACKAGE, substring = true)
        composeRule.onNodeWithTag(CLEAR_DATA_CONFIRM_TAG)
            .assertMinimumTouchTarget()
            .performClick()
        waitUntilTagGone(CLEAR_DATA_DIALOG_TAG)
        val dataClearedMessage = composeRule.activity.getString(
            string.feat_setting_extension_data_cleared
        )
        waitUntilExists(hasText(dataClearedMessage, substring = false))
        waitUntilGone(hasText(dataClearedMessage, substring = false))
        scrollDetailTo(actionTag("settings"))

        openSettings()
        composeRule.onNodeWithTag(choiceTag("auto")).performScrollTo().assertIsSelected()
        waitUntilTagGone(API_KEY_CLEAR_TAG)
        composeRule.onNodeWithTag(SETTINGS_SCREEN_TAG)
            .performScrollToNode(hasTestTag(API_ORIGIN_FIELD_TAG))
        waitUntilTagExists(API_ORIGIN_FIELD_TAG)
        composeRule.onNodeWithTag(API_ORIGIN_STATE_TAG, useUnmergedTree = true)
            .performScrollTo()
            .assertTextContains(notConfiguredOriginState, substring = false)
        closeSettings()

        scrollDetailTo(actionTag("reauthorize"))
        waitUntilTagEnabled(actionTag("reauthorize"))
        composeRule.onNodeWithTag(actionTag("reauthorize"))
            .assertMinimumTouchTarget()
            .performClick()
        waitUntilTagExists(AUTHORIZATION_SCREEN_TAG)
        scrollAuthorizationActionsIntoView()
        assertAuthorizationActionsAreUsable()
        physicallyClickAuthorization(string.feat_setting_extension_reauthorize)
        waitUntilTagGone(AUTHORIZATION_SCREEN_TAG)
        waitUntilTagExists(pluginDetailTag())
        scrollDetailTo(actionTag("disable"))
        waitUntilTagEnabled(actionTag("disable"))

        composeRule.onNodeWithTag(actionTag("disable"))
            .assertMinimumTouchTarget()
            .performClick()
        scrollDetailTo(actionTag("enable"))
        waitUntilTagEnabled(actionTag("enable"))
        composeRule.onNodeWithTag(actionTag("enable"))
            .assertMinimumTouchTarget()
            .performClick()
        waitUntilTagExists(AUTHORIZATION_SCREEN_TAG)
        scrollAuthorizationActionsIntoView()
        assertAuthorizationActionsAreUsable()
        physicallyClickAuthorization(string.feat_setting_extension_enable)
        waitUntilTagGone(AUTHORIZATION_SCREEN_TAG)
        waitUntilTagExists(pluginDetailTag())
        scrollDetailTo(actionTag("revoke"))
        waitUntilTagEnabled(actionTag("revoke"))

        composeRule.onNodeWithTag(actionTag("revoke"))
            .assertMinimumTouchTarget()
            .performClick()
        waitUntilTagExists(REVOKE_DIALOG_TAG)
        composeRule.onNodeWithTag(DATA_REMOVAL_PACKAGE_TAG)
            .assertTextContains(REFERENCE_PACKAGE, substring = true)
        composeRule.onNodeWithTag(REVOKE_CONFIRM_TAG)
            .assertMinimumTouchTarget()
            .performClick()
        waitUntilTagGone(REVOKE_DIALOG_TAG)
        scrollDetailTo(actionTag("enable"))
        waitUntilTagEnabled(actionTag("enable"))

        navigateBack()
        waitUntilTagExists(PLUGIN_LIST_TAG)
        waitUntilTagExists(pluginListItemTag())
        navigateBack()
        waitUntilTagExists(EXTENSION_ENTRY_TAG)
        assertAdaptiveNavigation(nestedDetailVisible = false)
    }

    private fun openSettings() {
        scrollDetailTo(actionTag("settings"))
        waitUntilTagEnabled(actionTag("settings"))
        composeRule.onNodeWithTag(actionTag("settings"))
            .assertMinimumTouchTarget()
            .performClick()
        waitUntilTagExists(SETTINGS_SCREEN_TAG)
    }

    private fun scrollAuthorizationActionsIntoView() {
        composeRule.onNodeWithTag(AUTHORIZATION_SCREEN_TAG)
            .performScrollToNode(hasTestTag(AUTHORIZATION_BOTTOM_SAFE_SPACE_TAG))
        waitUntilTagExists(AUTHORIZATION_BOTTOM_SAFE_SPACE_TAG)
        composeRule.waitForIdle()
    }

    private fun assertAuthorizationActionsAreUsable() {
        waitUntilTagEnabled(AUTHORIZATION_CONFIRM_TAG)
        composeRule.onNodeWithTag(AUTHORIZATION_CONFIRM_TAG)
            .assert(hasClickAction())
        assertFlowRowControlsDoNotOverlap(
            firstTag = AUTHORIZATION_CANCEL_TAG,
            secondTag = AUTHORIZATION_CONFIRM_TAG,
        )
    }

    private fun assertFlowRowControlsDoNotOverlap(
        firstTag: String,
        secondTag: String,
    ) {
        val first = composeRule.onNodeWithTag(firstTag)
            .performScrollTo()
            .assertIsDisplayed()
            .assertMinimumTouchTarget()
        val second = composeRule.onNodeWithTag(secondTag)
            .performScrollTo()
            .assertIsDisplayed()
            .assertMinimumTouchTarget()
        val firstBounds = first.getUnclippedBoundsInRoot()
        val secondBounds = second.getUnclippedBoundsInRoot()
        val rootBounds = composeRule.onRoot().getUnclippedBoundsInRoot()
        val overlapWidth =
            minOf(firstBounds.right, secondBounds.right) -
                maxOf(firstBounds.left, secondBounds.left)
        val overlapHeight =
            minOf(firstBounds.bottom, secondBounds.bottom) -
                maxOf(firstBounds.top, secondBounds.top)

        assertTrue(
            "FlowRow controls overlap: " +
                "$firstTag=$firstBounds, $secondTag=$secondBounds",
            overlapWidth <= 0.dp || overlapHeight <= 0.dp,
        )
        listOf(firstTag to firstBounds, secondTag to secondBounds).forEach { (tag, bounds) ->
            assertTrue(
                "FlowRow control is clipped by the root: " +
                    "$tag=$bounds, root=$rootBounds",
                bounds.left >= rootBounds.left &&
                    bounds.top >= rootBounds.top &&
                    bounds.right <= rootBounds.right &&
                    bounds.bottom <= rootBounds.bottom,
            )
        }
    }

    private fun assertBusyDetailActionsAreDisabledAndDoNotOverlap() {
        val actionTags = listOf(
            actionTag("settings"),
            actionTag("reauthorize"),
            actionTag("disable"),
        )
        composeRule.onNodeWithTag(pluginDetailTag())
            .performScrollToNode(hasTestTag(actionTags.last()))
        composeRule.waitForIdle()
        val rootBounds = composeRule.onNodeWithTag(pluginDetailTag())
            .getUnclippedBoundsInRoot()
        val actionBounds = actionTags.map { tag ->
            val node = composeRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertIsNotEnabled()
                .assertMinimumTouchTarget()
            tag to node.getUnclippedBoundsInRoot()
        }

        actionBounds.forEach { (tag, bounds) ->
            assertTrue(
                "Busy extension action is clipped: $tag=$bounds, root=$rootBounds",
                bounds.left >= rootBounds.left &&
                    bounds.top >= rootBounds.top &&
                    bounds.right <= rootBounds.right &&
                    bounds.bottom <= rootBounds.bottom,
            )
        }
        actionBounds.forEachIndexed { index, (firstTag, firstBounds) ->
            actionBounds.drop(index + 1).forEach { (secondTag, secondBounds) ->
                val overlapWidth =
                    minOf(firstBounds.right, secondBounds.right) -
                        maxOf(firstBounds.left, secondBounds.left)
                val overlapHeight =
                    minOf(firstBounds.bottom, secondBounds.bottom) -
                        maxOf(firstBounds.top, secondBounds.top)
                assertTrue(
                    "Busy extension actions overlap: " +
                        "$firstTag=$firstBounds, $secondTag=$secondBounds",
                    overlapWidth <= 0.dp || overlapHeight <= 0.dp,
                )
            }
        }
    }

    private fun assertTechnicalIdentityValue(key: String, expected: String) {
        val tag = "$TECHNICAL_IDENTITY_VALUE_TAG_PREFIX$key"
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains(expected, substring = true)
    }

    private fun assertRequestedAccessibilityConfigurationIfPresent() {
        val matrixCase = InstrumentationRegistry.getArguments()
            .getString(ARG_ACCESSIBILITY_MATRIX_CASE)
            ?: return
        if (matrixCase != MATRIX_CASE_COMPACT_RTL_LARGE) return

        val configuration = composeRule.activity.resources.configuration
        assertEquals(
            "The extension lifecycle must run in a locale-driven RTL configuration",
            LOCALE_RTL_TEST,
            configuration.locales[0].toLanguageTag(),
        )
        assertEquals(
            "The extension lifecycle must use RTL layout direction",
            View.LAYOUT_DIRECTION_RTL,
            configuration.layoutDirection,
        )
        assertTrue(
            "The extension lifecycle must run at 200% font scale; " +
                "actual=${configuration.fontScale}",
            configuration.fontScale >= LARGE_TEXT_MINIMUM_SCALE,
        )
        assertTrue(
            "The extension lifecycle must use a 320dp compact window; " +
                "actual=${configuration.screenWidthDp}",
            configuration.screenWidthDp in COMPACT_WIDTH_RANGE,
        )
    }

    private fun assertAdaptiveNavigation(nestedDetailVisible: Boolean) {
        val usesSideRail =
            composeRule.activity.resources.configuration.screenWidthDp >=
                SIDE_RAIL_MINIMUM_WIDTH_DP
        if (usesSideRail) {
            waitUntilTagGone(FLOATING_NAVIGATION_TAG)
            waitUntilTagExists(SIDE_NAVIGATION_SETTING_TAG)
            composeRule.onNodeWithTag(SIDE_NAVIGATION_SETTING_TAG)
                .assertIsSelected()
        } else if (nestedDetailVisible) {
            waitUntilTagGone(FLOATING_NAVIGATION_TAG)
        } else {
            waitUntilTagExists(FLOATING_NAVIGATION_TAG)
        }
    }

    private fun scrollDetailTo(tag: String) {
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            runCatching {
                composeRule.onNodeWithTag(pluginDetailTag())
                    .performScrollToNode(hasTestTag(tag))
            }.isSuccess
        }
        waitUntilTagExists(tag)
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            runCatching {
                composeRule.onNodeWithTag(tag).performScrollTo()
            }.isSuccess
        }
    }

    private fun physicallyClickAuthorization(labelResource: Int) {
        val label = composeRule.activity.getString(labelResource)
        val expectedBounds = composeRule.onNodeWithTag(AUTHORIZATION_CONFIRM_TAG)
            .fetchSemanticsNode()
            .boundsInWindow
        // Compose exposes the control role through semantics, but UIAutomator
        // does not consistently map that role to android.widget.Button.
        val selector = By.text(label).enabled(true)
        val deadline = SystemClock.uptimeMillis() + UI_TIMEOUT_MILLIS

        while (SystemClock.uptimeMillis() < deadline) {
            val clicked = device.findObjects(selector).any { control ->
                try {
                    val bounds = control.visibleBounds
                    val centerX = bounds.exactCenterX()
                    val centerY = bounds.exactCenterY()
                    val matchesComposeControl =
                        centerX >= expectedBounds.left &&
                            centerX <= expectedBounds.right &&
                            centerY >= expectedBounds.top &&
                            centerY <= expectedBounds.bottom
                    if (matchesComposeControl) control.click()
                    matchesComposeControl
                } catch (_: StaleObjectException) {
                    false
                }
            }
            if (clicked) {
                device.waitForIdle()
                composeRule.waitForIdle()
                return
            }
            SystemClock.sleep(UI_AUTOMATOR_RETRY_MILLIS)
        }

        throw AssertionError("Authorization action is not visible: $label")
    }

    private fun closeSettings() {
        navigateBack()
        waitUntilTagGone(SETTINGS_SCREEN_TAG)
        waitUntilTagExists(pluginDetailTag())
    }

    private fun navigateBack() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun openExtensionPlugins() {
        val pluginSettings =
            composeRule.activity.getString(string.feat_setting_extension_plugins)
        waitUntilExists(
            hasText(pluginSettings, substring = false, ignoreCase = true) or
                hasContentDescription(
                    composeRule.activity.getString(string.ui_destination_setting),
                    substring = false,
                    ignoreCase = true,
                )
        )
        if (
            composeRule.onAllNodesWithTag(EXTENSION_ENTRY_TAG)
                .fetchSemanticsNodes()
                .isEmpty()
        ) {
            composeRule.onNode(
                hasContentDescription(
                    composeRule.activity.getString(string.ui_destination_setting),
                    substring = false,
                    ignoreCase = true,
                ) and hasClickAction()
            ).performClick()
        }
        waitUntilTagExists(EXTENSION_ENTRY_TAG)
        composeRule.onAllNodesWithTag(EXTENSION_ENTRY_TAG).assertCountEquals(1)
        composeRule.onNodeWithTag(EXTENSION_ENTRY_TAG)
            .assertMinimumTouchTarget()
            .performClick()
        waitUntilTagExists(PLUGIN_LIST_TAG)
    }

    private fun SemanticsNodeInteraction.assertMinimumTouchTarget():
        SemanticsNodeInteraction = assertWidthIsAtLeast(48.dp)
        .assertHeightIsAtLeast(48.dp)

    private fun SemanticsNodeInteraction.assertDisclosureState(
        stateResource: Int,
    ): SemanticsNodeInteraction {
        val matcher = SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription,
            composeRule.activity.getString(stateResource),
        )
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            runCatching { assert(matcher) }.isSuccess
        }
        return this
    }

    private fun waitUntilExists(matcher: SemanticsMatcher) {
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilGone(matcher: SemanticsMatcher) {
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitUntilTagExists(tag: String) {
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilTagEnabled(tag: String) {
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasTestTag(tag) and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitUntilTagGone(tag: String) {
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun pluginListItemTag(): String =
        "extension-plugin-list-item:$REFERENCE_PACKAGE/$REFERENCE_SERVICE"

    private fun pluginDetailTag(): String =
        "extension-plugin-detail:$REFERENCE_PACKAGE/$REFERENCE_SERVICE"

    private fun actionTag(action: String): String =
        "extension-plugin-action-$action:$REFERENCE_PACKAGE/$REFERENCE_SERVICE"

    private fun choiceTag(value: String): String =
        "extension-setting-choice:playback/quality:$value"

    private companion object {
        const val UI_TIMEOUT_MILLIS = 15_000L
        const val UI_AUTOMATOR_RETRY_MILLIS = 100L
        const val REFERENCE_PACKAGE = "com.m3u.testing.extension.reference"
        const val REFERENCE_SERVICE =
            "com.m3u.testing.extension.reference.ReferenceExtensionService"
        const val REFERENCE_CAPABILITY_ID = "background.task"
        const val EXTENSION_ENTRY_TAG = "extension-entry"
        const val FLOATING_NAVIGATION_TAG = "floating-app-navigation"
        const val SIDE_NAVIGATION_SETTING_TAG = "side-navigation-item:Setting"
        const val PLUGIN_LIST_TAG = "extension-list"
        const val PLUGIN_DETAIL_LOADING_TAG = "extension-plugin-detail-loading"
        const val PLUGIN_FAILURE_TAG = "extension-plugin-failure"
        const val PLUGIN_RETRY_TAG = "extension-plugin-retry"
        const val PLUGIN_UNAVAILABLE_TAG = "extension-plugin-unavailable"
        const val OPERATION_PROGRESS_TAG = "extension-operation-progress"
        const val CAPABILITIES_DISCLOSURE_TAG = "extension-capabilities-disclosure"
        const val NETWORK_ORIGINS_DISCLOSURE_TAG =
            "extension-network-origins-disclosure"
        const val TECHNICAL_IDENTITY_DISCLOSURE_TAG =
            "extension-technical-identity-disclosure"
        const val TECHNICAL_IDENTITY_VALUE_TAG_PREFIX =
            "extension-technical-identity-value:"
        const val AUTHORIZATION_SCREEN_TAG = "extension-authorization"
        const val AUTHORIZATION_IDENTITY_DISCLOSURE_TAG =
            "extension-authorization-identity-disclosure"
        const val AUTHORIZATION_BOTTOM_SAFE_SPACE_TAG =
            "extension-authorization-bottom-safe-space"
        const val AUTHORIZATION_CONFIRM_TAG = "extension-authorization-confirm"
        const val AUTHORIZATION_CANCEL_TAG = "extension-authorization-cancel"
        const val SETTINGS_SCREEN_TAG = "extension-settings"
        const val CLEAR_DATA_DIALOG_TAG = "extension-clear-data-dialog"
        const val CLEAR_DATA_CONFIRM_TAG = "extension-clear-data-confirm"
        const val REVOKE_DIALOG_TAG = "extension-revoke-dialog"
        const val REVOKE_CONFIRM_TAG = "extension-revoke-confirm"
        const val DATA_REMOVAL_PACKAGE_TAG = "extension-data-removal-package"
        const val API_KEY_FIELD_TAG = "extension-setting-field:manifest/api-key"
        const val API_KEY_SAVE_TAG = "extension-setting-save:manifest/api-key"
        const val API_KEY_CLEAR_TAG = "extension-setting-clear:manifest/api-key"
        const val API_ORIGIN_FIELD_TAG =
            "extension-setting-field:playback/api-origin"
        const val API_ORIGIN_SAVE_TAG =
            "extension-setting-save:playback/api-origin"
        const val API_ORIGIN_STATE_TAG =
            "extension-setting-origin-state:playback/api-origin"
        const val API_ORIGIN_DETAIL_TAG =
            "extension-network-setting-origin:playback/api-origin"
        const val REFERENCE_SETTING_ORIGIN = "https://ui.reference.test:443"
        const val ARG_ACCESSIBILITY_MATRIX_CASE = "accessibilityMatrixCase"
        const val MATRIX_CASE_COMPACT_RTL_LARGE = "compact-rtl-large"
        const val LOCALE_RTL_TEST = "ar-XB"
        const val LARGE_TEXT_MINIMUM_SCALE = 1.95f
        const val SIDE_RAIL_MINIMUM_WIDTH_DP = 600
        const val REFERENCE_EXTENSION_ID = "com.m3u.reference.provider"
        const val LONG_REFERENCE_VERSION =
            "1.0.0-reference-preview.20260729"
        val COMPACT_WIDTH_RANGE = 315..325
    }
}
