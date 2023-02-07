package com.m3u.features.setting

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.annotation.SetSyncMode
import com.m3u.core.annotation.SyncMode
import com.m3u.core.util.context.toast
import com.m3u.features.setting.components.CheckBoxPreference
import com.m3u.features.setting.components.FoldPreference
import com.m3u.features.setting.components.TextPreference
import com.m3u.ui.components.M3UColumn
import com.m3u.ui.components.M3UTextButton
import com.m3u.ui.components.M3UTextField
import com.m3u.ui.model.AppAction
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.SetActions
import com.m3u.ui.util.EventHandler
import com.m3u.ui.util.LifecycleEffect

@Composable
internal fun SettingRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingViewModel = hiltViewModel(),
    setAppActions: SetActions
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    EventHandler(state.message) {
        context.toast(it)
    }

    val setAppActionsUpdated by rememberUpdatedState(setAppActions)
    LifecycleEffect { event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                val actions = listOf<AppAction>()
                setAppActionsUpdated(actions)
            }

            Lifecycle.Event.ON_PAUSE -> {
                setAppActionsUpdated(emptyList())
            }

            else -> {}
        }
    }

    SettingScreen(
        subscribeEnable = !state.adding,
        title = state.title,
        url = state.url,
        version = state.version,
        syncMode = state.syncMode,
        onTitle = { viewModel.onEvent(SettingEvent.OnTitle(it)) },
        onUrl = { viewModel.onEvent(SettingEvent.OnUrl(it)) },
        onSubscribe = { viewModel.onEvent(SettingEvent.SubscribeUrl) },
        onSyncMode = { viewModel.onEvent(SettingEvent.OnSyncMode(it)) },
        useCommonUIMode = state.useCommonUIMode,
        onUIMode = { viewModel.onEvent(SettingEvent.OnUIMode) },
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun SettingScreen(
    subscribeEnable: Boolean,
    version: String,
    title: String,
    url: String,
    @SyncMode syncMode: Int,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    onSyncMode: SetSyncMode,
    useCommonUIMode: Boolean,
    onUIMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = Modifier.testTag("features:setting")
    ) {
        val configuration = LocalConfiguration.current
        when (configuration.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                PortraitOrientationContent(
                    title = title,
                    url = url,
                    subscribeEnable = subscribeEnable,
                    onTitle = onTitle,
                    onUrl = onUrl,
                    onSubscribe = onSubscribe,
                    syncMode = syncMode,
                    onSyncMode = onSyncMode,
                    version = version,
                    modifier = modifier
                        .fillMaxWidth()
                        .scrollable(
                            orientation = Orientation.Vertical,
                            state = rememberScrollableState { it }
                        )
                )
            }

            Configuration.ORIENTATION_LANDSCAPE -> {
                LandscapeOrientationContent(
                    title = title,
                    url = url,
                    subscribeEnable = subscribeEnable,
                    onTitle = onTitle,
                    onUrl = onUrl,
                    onSubscribe = onSubscribe,
                    syncMode = syncMode,
                    onSyncMode = onSyncMode,
                    useCommonUIMode = useCommonUIMode,
                    onUIMode = onUIMode,
                    version = version,
                    modifier = modifier
                        .scrollable(
                            orientation = Orientation.Vertical,
                            state = rememberScrollableState { it }
                        )
                )
            }

            else -> {}
        }
    }
}

@Composable
private fun PortraitOrientationContent(
    title: String,
    url: String,
    subscribeEnable: Boolean,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    syncMode: @SyncMode Int,
    onSyncMode: SetSyncMode,
    version: String,
    modifier: Modifier = Modifier
) {
    var fold: Fold by remember { mutableStateOf(Fold.NONE) }
    when (fold) {
        Fold.NONE -> {
            PreferencesPart(
                version = version,
                onSubscriptionManagement = {
                    fold = Fold.SUBSCRIPTION
                },
                onScriptManagement = {
                    fold = Fold.SCRIPT
                },
                syncMode = syncMode,
                onSyncMode = onSyncMode,
                useCommonUIMode = true,
                useCommonUIModeEnable = false,
                onUIMode = { },
                modifier = modifier
            )
        }

        Fold.SUBSCRIPTION -> {
            SubscriptionManagementPart(
                title = title,
                url = url,
                subscribeEnable = subscribeEnable,
                onTitle = onTitle,
                onUrl = onUrl,
                onSubscribe = onSubscribe,
                modifier = modifier
            )
        }

        Fold.SCRIPT -> {
            ScriptManagementPart(
                modifier = modifier
            )
        }
    }

    BackHandler(fold != Fold.NONE) {
        fold = Fold.NONE
    }
}

@Composable
private fun LandscapeOrientationContent(
    title: String,
    url: String,
    subscribeEnable: Boolean,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    syncMode: @SyncMode Int,
    onSyncMode: SetSyncMode,
    useCommonUIMode: Boolean,
    onUIMode: () -> Unit,
    version: String,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    var fold: Fold by remember { mutableStateOf(Fold.NONE) }
    fun setFold(target: Fold) {
        fold = (if (fold == target) Fold.NONE else target)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.medium, Alignment.Start),
        modifier = modifier.padding(horizontal = spacing.medium)
    ) {
        PreferencesPart(
            version = version,
            onSubscriptionManagement = { setFold(Fold.SUBSCRIPTION) },
            onScriptManagement = { setFold(Fold.SCRIPT) },
            syncMode = syncMode,
            onSyncMode = onSyncMode,
            useCommonUIMode = useCommonUIMode,
            onUIMode = onUIMode,
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
        )

        when (fold) {
            Fold.SUBSCRIPTION -> {
                SubscriptionManagementPart(
                    title = title,
                    url = url,
                    subscribeEnable = subscribeEnable,
                    onTitle = onTitle,
                    onUrl = onUrl,
                    onSubscribe = onSubscribe,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                )
            }

            Fold.SCRIPT -> {
                ScriptManagementPart(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                )
            }

            else -> {}
        }
    }
    BackHandler(fold != Fold.NONE) {
        fold = Fold.NONE
    }
}

@Composable
private fun PreferencesPart(
    version: String,
    onSubscriptionManagement: () -> Unit,
    onScriptManagement: () -> Unit,
    syncMode: @SyncMode Int,
    onSyncMode: SetSyncMode,
    useCommonUIMode: Boolean,
    onUIMode: () -> Unit,
    modifier: Modifier = Modifier,
    useCommonUIModeEnable: Boolean = true,
) {
    val spacing = LocalSpacing.current
    M3UColumn(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.clip(RoundedCornerShape(spacing.medium)),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            FoldPreference(
                title = stringResource(R.string.subscription_management),
                enabled = true,
                onClick = onSubscriptionManagement
            )
            FoldPreference(
                title = stringResource(R.string.script_management),
                enabled = true,
                onClick = onScriptManagement
            )
            TextPreference(
                title = stringResource(R.string.sync_mode),
                content = when (syncMode) {
                    SyncMode.DEFAULT -> stringResource(R.string.sync_mode_default)
                    SyncMode.EXCEPT -> stringResource(R.string.sync_mode_favourite_except)
                    else -> ""
                },
                onClick = {
                    // TODO
                    val target = when (syncMode) {
                        SyncMode.DEFAULT -> SyncMode.EXCEPT
                        else -> SyncMode.DEFAULT
                    }
                    onSyncMode(target)
                }
            )
            CheckBoxPreference(
                title = stringResource(R.string.common_ui_mode),
                subtitle = stringResource(R.string.common_ui_mode_description),
                enabled = useCommonUIModeEnable,
                checked = useCommonUIMode,
                onCheckedChange = { newValue ->
                    if (newValue != useCommonUIMode) {
                        onUIMode()
                    }
                }
            )
        }
        val uriHandler = LocalUriHandler.current
        M3UTextButton(
            text = stringResource(R.string.label_app_version, version),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            val url = "https://github.com/thxbrop/M3UAndroid/releases/tag/v$version"
            uriHandler.openUri(url)
        }
    }
}

@Composable
private fun SubscriptionManagementPart(
    title: String,
    url: String,
    subscribeEnable: Boolean,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    M3UColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        modifier = modifier
    ) {
        val focusRequester = remember { FocusRequester() }
        M3UTextField(
            text = title,
            placeholder = stringResource(R.string.placeholder_title),
            onValueChange = onTitle,
            keyboardActions = KeyboardActions(
                onNext = {
                    focusRequester.requestFocus()
                }
            ),
            modifier = Modifier.fillMaxWidth()
        )
        M3UTextField(
            text = url,
            placeholder = stringResource(R.string.placeholder_url),
            onValueChange = onUrl,
            keyboardActions = KeyboardActions(
                onDone = {
                    onSubscribe()
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
        val buttonTextResId = if (subscribeEnable) R.string.label_subscribe
        else R.string.label_subscribing
        M3UTextButton(
            enabled = subscribeEnable,
            text = stringResource(buttonTextResId),
            onClick = { onSubscribe() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ScriptManagementPart(
    modifier: Modifier = Modifier
) {
    M3UColumn(
        modifier = modifier
    ) {

    }
}

private enum class Fold {
    NONE, SUBSCRIPTION, SCRIPT
}