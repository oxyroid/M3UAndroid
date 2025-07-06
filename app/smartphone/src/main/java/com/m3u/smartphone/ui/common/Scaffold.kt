package com.m3u.smartphone.ui.common

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxOfOrNull
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import com.m3u.core.foundation.ui.composableOf
import com.m3u.data.database.model.Channel
import com.m3u.data.service.MediaCommand
import com.m3u.i18n.R
import com.m3u.smartphone.ui.business.playlist.components.ChannelGallery
import com.m3u.smartphone.ui.common.helper.Fob
import com.m3u.smartphone.ui.common.helper.LocalHelper
import com.m3u.smartphone.ui.common.helper.Metadata
import com.m3u.smartphone.ui.common.helper.useRailNav
import com.m3u.smartphone.ui.common.internal.SmartphoneScaffoldImpl
import com.m3u.smartphone.ui.common.internal.TabletScaffoldImpl
import com.m3u.smartphone.ui.material.components.Background
import com.m3u.smartphone.ui.material.components.Destination
import com.m3u.smartphone.ui.material.components.FontFamilies
import com.m3u.smartphone.ui.material.effects.currentBackStackEntry
import com.m3u.smartphone.ui.material.ktx.plus
import com.m3u.smartphone.ui.material.model.LocalHazeState
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.util.lerp as lerpf

@Composable
@OptIn(InternalComposeApi::class)
internal fun Scaffold(
    rootDestination: Destination.Root?,
    navigateToRootDestination: (Destination.Root) -> Unit,
    modifier: Modifier = Modifier,
    onBackPressed: (() -> Unit)? = null,
    navigateToChannel: () -> Unit,
    alwaysShowLabel: Boolean = false,
    content: @Composable BoxScope.(PaddingValues) -> Unit
) {
    val useRailNav = LocalHelper.current.useRailNav

    val hazeState = remember { HazeState() }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Background {
            when {
                !useRailNav -> {
                    SmartphoneScaffoldImpl(
                        rootDestination = rootDestination,
                        alwaysShowLabel = alwaysShowLabel,
                        navigateToRoot = navigateToRootDestination,
                        onBackPressed = onBackPressed,
                        navigateToChannel = navigateToChannel,
                        content = content,
                        modifier = modifier
                    )
                }

                else -> {
                    TabletScaffoldImpl(
                        rootDestination = rootDestination,
                        alwaysShowLabel = alwaysShowLabel,
                        navigateToRoot = navigateToRootDestination,
                        navigateToChannel = navigateToChannel,
                        onBackPressed = onBackPressed,
                        content = content,
                        modifier = modifier
                    )
                }
            }
        }
    }
}


@Composable
internal fun Items(
    inner: @Composable (Destination.Root) -> Unit
) {
    Destination.Root.entries.fastForEach { rootDestination ->
        inner(rootDestination)
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
internal fun MainContent(
    windowInsets: WindowInsets,
    showQuery: Boolean,
    onShowQueryChange: (Boolean) -> Unit,
    onBackPressed: (() -> Unit)?,
    navigateToChannel: () -> Unit,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
    viewModel: SmartphoneViewModel = hiltViewModel()
) {
    val density = LocalDensity.current
    val helper = LocalHelper.current
    val hazeState = LocalHazeState.current

    val coroutineScope = rememberCoroutineScope()

    val title = Metadata.title
    val actions = Metadata.actions

    val backStackEntry by currentBackStackEntry()

    val query by viewModel.query.collectAsStateWithLifecycle()
    val onQueryChange = { query: String -> viewModel.query.value = query }
    val channels: Flow<PagingData<Channel>> = viewModel.channels

    Background {
        val headlineFraction = Metadata.headlineFraction
        val fraction = LinearOutSlowInEasing.transform(headlineFraction)
        val color = lerpColor(Color.Transparent, MaterialTheme.colorScheme.surface, fraction)
        val blurRadius = lerpDp(0.dp, 20.dp, fraction)
        val noiseFactor = lerpf(0f, 0.15f, fraction)

        val currentHazeColor = if (showQuery) MaterialTheme.colorScheme.surface else color
        val currentBlurRadius = if (showQuery) 30.dp else blurRadius
        val currentNoiseFactor = if (showQuery) 0.15f else noiseFactor
        val currentOnShowQueryChange = { value: Boolean ->
            if (!value) {
                viewModel.query.value = ""
            }
            onShowQueryChange(value)
        }
        var searchBarHeight by remember { mutableStateOf(0.dp) }
        // androidx.compose.material3.SearchBarVerticalPadding
        val SearchBarVerticalPadding: Dp = 8.dp
        StarBackground()
        content(windowInsets.asPaddingValues() + PaddingValues(top = searchBarHeight + SearchBarVerticalPadding))
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = {},
                    expanded = showQuery,
                    onExpandedChange = currentOnShowQueryChange,
                    placeholder = {
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(
                                text = if (!showQuery) title
                                else AnnotatedString(stringResource(R.string.feat_playlist_query_placeholder)),
                                maxLines = 1,
                                style = MaterialTheme.typography.titleLarge,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = FontFamilies.LexendExa,
                                color = LocalContentColor.current.copy(
                                    alpha = if (showQuery) 0.65f else 1f
                                )
                            )
                        }
                    },
                    leadingIcon = onBackPressed?.let { onClick ->
                        composableOf {
                            IconButton(onClick) {
                                Icon(
                                    imageVector = backStackEntry?.navigationIcon
                                        ?: Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                    trailingIcon = composableOf(!showQuery) {
                        Row {
                            actions.forEach { action ->
                                IconButton(
                                    onClick = action.onClick,
                                    enabled = action.enabled
                                ) {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = action.contentDescription,
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.onLayoutRectChanged {
                        searchBarHeight = with(density) { it.height.toDp() }
                    }
                )
            },
            expanded = showQuery,
            onExpandedChange = currentOnShowQueryChange,
            colors = SearchBarDefaults.colors(Color.Transparent),
            modifier = Modifier
                .hazeEffect(
                    state = hazeState,
                    style = HazeDefaults.style(
                        backgroundColor = currentHazeColor,
                        blurRadius = currentBlurRadius,
                        noiseFactor = currentNoiseFactor
                    )
                )
                .fillMaxWidth()
        ) {
            val state = rememberLazyStaggeredGridState()
            val lazy: Flow<PagingData<Channel>> by produceState(emptyFlow(), channels) {
                delay(300.milliseconds)
                value = channels
            }
            ChannelGallery(
                state = state,
                rowCount = 1,
                channels = lazy,
                zapping = null,
                recently = false,
                isVodOrSeriesPlaylist = false,
                onClick = { channel ->
                    coroutineScope.launch {
                        helper.play(MediaCommand.Common(channel.id))
                        navigateToChannel()
                    }
                },
                onLongClick = {},
                getProgrammeCurrently = { null },
                reloadThumbnail = { null },
                syncThumbnail = { null },
                contentPadding = WindowInsets.ime.asPaddingValues()
            )
        }
        HazeMaterials.regular()
    }
}

@Composable
internal fun NavigationItemLayout(
    rootDestination: Destination.Root?,
    fob: Fob?,
    currentRootDestination: Destination.Root,
    navigateToRoot: (Destination.Root) -> Unit,
    block: @Composable (
        selected: Boolean,
        onClick: () -> Unit,
        icon: @Composable () -> Unit,
        label: @Composable () -> Unit
    ) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current

    val usefob = fob?.rootDestination == currentRootDestination
    val selected = usefob || currentRootDestination == rootDestination
    val icon = @Composable {
        Icon(
            imageVector = when {
                fob != null && usefob -> fob.icon
                selected -> currentRootDestination.selectedIcon
                else -> currentRootDestination.unselectedIcon
            },
            contentDescription = null
        )
    }
    val label: @Composable () -> Unit = remember(usefob, fob) {
        @Composable {
            Text(
                text = stringResource(
                    if (usefob) fob.iconTextId
                    else currentRootDestination.iconTextId
                ).uppercase()
            )
        }
    }
    val actualOnClick: () -> Unit = if (usefob) {
        {
            fob.onClick()
        }
    } else {
        {
            if (currentRootDestination != rootDestination) {
                navigateToRoot(currentRootDestination)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }
    block(selected, actualOnClick, icon, label)
}

internal enum class ScaffoldContent { Navigation, MainContent }
internal enum class ScaffoldRole { SmartPhone, Tablet }

@Composable
internal fun ScaffoldLayout(
    role: ScaffoldRole,
    navigation: @Composable () -> Unit,
    mainContent: @Composable (PaddingValues) -> Unit
) {
    SubcomposeLayout { constraints ->
        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val navigationPlaceables = subcompose(
            slotId = ScaffoldContent.Navigation,
            content = navigation
        )
            .fastMap { it.measure(looseConstraints) }
        val navigationWidth = navigationPlaceables.fastMaxOfOrNull { it.width } ?: 0
        val navigationHeight = navigationPlaceables.fastMaxOfOrNull { it.height } ?: 0
        val mainContentPadding = when (role) {
            ScaffoldRole.SmartPhone -> PaddingValues(
                bottom = navigationHeight.toDp()
            )

            else -> PaddingValues()
        }
        val mainContentPlaceables = subcompose(ScaffoldContent.MainContent) {
            mainContent(mainContentPadding)
        }
            .fastMap {
                when (role) {
                    ScaffoldRole.SmartPhone -> it.measure(looseConstraints)
                    else -> {
                        it.measure(
                            looseConstraints.offset(horizontal = -navigationWidth)
                        )
                    }
                }
            }
        layout(layoutWidth, layoutHeight) {
            when (role) {
                ScaffoldRole.SmartPhone -> {
                    mainContentPlaceables.fastForEach {
                        it.place(0, 0)
                    }
                    navigationPlaceables.fastForEach {
                        it.place(0, layoutHeight - navigationHeight)
                    }
                }

                else -> {
                    // rtl
                    navigationPlaceables.fastForEach {
                        it.placeRelative(0, 0)
                    }
                    mainContentPlaceables.fastForEach {
                        it.placeRelative(navigationWidth, 0)
                    }
                }
            }
        }
    }
}
