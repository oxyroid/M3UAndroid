package com.m3u.features.setting

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.AnimatedPane
import androidx.compose.material3.adaptive.HingePolicy
import androidx.compose.material3.adaptive.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.PaneScaffoldDirective
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.calculateListDetailPaneScaffoldState
import androidx.compose.material3.adaptive.calculatePosture
import androidx.compose.material3.adaptive.collectFoldingFeaturesAsState
import androidx.compose.material3.adaptive.currentWindowSize
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.data.database.entity.Stream
import com.m3u.features.setting.fragments.ScriptsFragment
import com.m3u.features.setting.fragments.SubscriptionsFragment
import com.m3u.features.setting.fragments.preferences.PreferencesFragment
import com.m3u.ui.EventHandler
import com.m3u.ui.LocalHelper
import com.m3u.ui.MessageEventHandler
import com.m3u.ui.ResumeEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun SettingRoute(
    modifier: Modifier = Modifier,
    resume: ResumeEvent,
    contentPadding: PaddingValues,
    viewModel: SettingViewModel = hiltViewModel(),
    navigateToConsole: () -> Unit,
    navigateToAbout: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val banneds by viewModel.banneds.collectAsStateWithLifecycle()
    val helper = LocalHelper.current

    EventHandler(resume) {
        helper.actions = persistentListOf()
    }

    MessageEventHandler(message)

    val controller = LocalSoftwareKeyboardController.current
    SettingScreen(
        contentPadding = contentPadding,
        versionName = state.versionName,
        versionCode = state.versionCode,
        title = state.title,
        url = state.url,
        uriWrapper = rememberUriWrapper(state.uri),
        navigateToConsole = navigateToConsole,
        banneds = banneds,
        onTitle = { viewModel.onEvent(SettingEvent.OnTitle(it)) },
        onUrl = { viewModel.onEvent(SettingEvent.OnUrl(it)) },
        onSubscribe = {
            controller?.hide()
            viewModel.onEvent(SettingEvent.Subscribe)
        },
        onBanned = { viewModel.onEvent(SettingEvent.OnBanned(it)) },
        importJavaScript = { viewModel.onEvent(SettingEvent.ImportJavaScript(it)) },
        navigateToAbout = navigateToAbout,
        localStorage = state.localStorage,
        onLocalStorage = { viewModel.onEvent(SettingEvent.OnLocalStorage) },
        openDocument = { viewModel.onEvent(SettingEvent.OpenDocument(it)) },
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun SettingScreen(
    contentPadding: PaddingValues,
    versionName: String,
    versionCode: Int,
    title: String,
    url: String,
    uriWrapper: UriWrapper,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    banneds: ImmutableList<Stream>,
    onBanned: (Int) -> Unit,
    importJavaScript: (Uri) -> Unit,
    navigateToConsole: () -> Unit,
    navigateToAbout: () -> Unit,
    localStorage: Boolean,
    onLocalStorage: () -> Unit,
    openDocument: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    var fragment: SettingFragment by rememberSaveable { mutableStateOf(SettingFragment.Root) }
    var currentPaneDestination by rememberSaveable {
        mutableStateOf(ListDetailPaneScaffoldRole.List)
    }
    val scaffoldState = calculateListDetailPaneScaffoldState(
        currentPaneDestination = currentPaneDestination,
        scaffoldDirective = calculateStandardPaneScaffoldDirective(
            WindowAdaptiveInfo(
                WindowSizeClass.calculateFromSize(
                    with(LocalDensity.current) {
                        currentWindowSize().toSize().toDpSize()
                    }
                ),
                calculatePosture(collectFoldingFeaturesAsState().value)
            ),
            HingePolicy.NeverAvoid
        )
    )

    ListDetailPaneScaffold(
        scaffoldState = scaffoldState,
        listPane = {
            PreferencesFragment(
                fragment = fragment,
                contentPadding = contentPadding,
                versionName = versionName,
                versionCode = versionCode,
                navigateToPlaylistManagement = {
                    currentPaneDestination = ListDetailPaneScaffoldRole.Detail
                    fragment = SettingFragment.Subscriptions
                },
                navigateToScriptManagement = {
                    currentPaneDestination = ListDetailPaneScaffoldRole.Detail
                    fragment = (SettingFragment.Scripts)
                },
                navigateToConsole = navigateToConsole,
                navigateToAbout = navigateToAbout,
                modifier = Modifier.fillMaxSize()
            )
        },
        detailPane = {
            if (fragment != SettingFragment.Root) {
                AnimatedPane(Modifier) {
                    when (fragment) {
                        SettingFragment.Subscriptions -> {
                            SubscriptionsFragment(
                                contentPadding = contentPadding,
                                title = title,
                                url = url,
                                uriWrapper = uriWrapper,
                                banneds = banneds,
                                onBanned = onBanned,
                                onTitle = onTitle,
                                onUrl = onUrl,
                                onSubscribe = onSubscribe,
                                localStorage = localStorage,
                                onLocalStorage = onLocalStorage,
                                openDocument = openDocument,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        SettingFragment.Scripts -> {
                            ScriptsFragment(
                                contentPadding = contentPadding,
                                importJavaScript = importJavaScript,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        else -> {}
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .testTag("feature:setting")
    )

    BackHandler(fragment != SettingFragment.Root) {
        fragment = SettingFragment.Root
        currentPaneDestination = ListDetailPaneScaffoldRole.List
    }
}

private fun calculateStandardPaneScaffoldDirective(
    windowAdaptiveInfo: WindowAdaptiveInfo,
    verticalHingePolicy: HingePolicy = HingePolicy.AvoidSeparating
): PaneScaffoldDirective {
    val maxHorizontalPartitions: Int
    val contentPadding: PaddingValues
    val verticalSpacerSize: Dp
    when (windowAdaptiveInfo.windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            maxHorizontalPartitions = 1
            contentPadding = PaddingValues(0.dp)
            verticalSpacerSize = 0.dp
        }

        WindowWidthSizeClass.Medium -> {
            maxHorizontalPartitions = 1
            contentPadding = PaddingValues(0.dp)
            verticalSpacerSize = 0.dp
        }

        else -> {
            maxHorizontalPartitions = 2
            contentPadding = PaddingValues(0.dp)
            verticalSpacerSize = 24.dp
        }
    }
    val maxVerticalPartitions: Int
    val horizontalSpacerSize: Dp

    if (windowAdaptiveInfo.windowPosture.isTabletop) {
        maxVerticalPartitions = 2
        horizontalSpacerSize = 24.dp
    } else {
        maxVerticalPartitions = 1
        horizontalSpacerSize = 0.dp
    }

    return PaneScaffoldDirective(
        contentPadding,
        maxHorizontalPartitions,
        verticalSpacerSize,
        maxVerticalPartitions,
        horizontalSpacerSize,
        getExcludedVerticalBounds(windowAdaptiveInfo.windowPosture, verticalHingePolicy)
    )
}

private fun getExcludedVerticalBounds(posture: Posture, hingePolicy: HingePolicy): List<Rect> {
    return when (hingePolicy) {
        HingePolicy.AvoidSeparating -> posture.separatingVerticalHingeBounds
        HingePolicy.AvoidOccluding -> posture.occludingVerticalHingeBounds
        HingePolicy.AlwaysAvoid -> posture.allVerticalHingeBounds
        else -> emptyList()
    }
}