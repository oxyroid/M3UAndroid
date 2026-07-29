package com.m3u.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.business.setting.ExtensionSettingInputError
import com.m3u.business.setting.ProviderDiscoveryState
import com.m3u.business.setting.ProviderSettingFieldError
import com.m3u.business.setting.ProviderSubscriptionForm
import com.m3u.business.setting.extensionSettingInputError
import com.m3u.business.setting.normalizedExtensionSettingValue
import com.m3u.business.setting.supports
import com.m3u.core.foundation.util.basic.title
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.repository.extension.ExtensionSettingEditToken
import com.m3u.data.repository.extension.ExtensionSettingsConfiguration
import com.m3u.data.repository.provider.ProviderAccountSummary
import com.m3u.data.repository.provider.SubscriptionProviderExecutionKind
import com.m3u.data.repository.plugin.InstalledPlugin
import com.m3u.data.repository.plugin.PluginAuthorizationToken
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingKeys
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.ExtensionState
import com.m3u.i18n.R.string
import com.m3u.i18n.R.plurals
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

@Composable
fun TvBrowsePane(
    destination: TvDestination,
    state: TvUiState,
    onOpenLibrary: () -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onRefresh: () -> Unit,
    onPlay: (Channel) -> Unit,
    onPlayRecent: () -> Unit,
    onExternalExtensionsEnabled: (Boolean) -> Unit,
    onEnableExtension: (String, String, PluginAuthorizationToken) -> Unit,
    onReauthorizeExtension: (String, String, PluginAuthorizationToken) -> Unit,
    onDisableExtension: (String) -> Unit,
    onRevokeExtension: (String, String, String?) -> Unit,
    onClearExtensionData: (String, String, String?) -> Unit,
    onExportExtensionDiagnostics: (String) -> Unit,
    onOpenExtensionSettings: (String) -> Unit,
    onCloseExtensionSettings: () -> Unit,
    onUpdateExtensionSetting: (String, String, ExtensionSettingEditToken, String?) -> Unit,
    onRefreshProviders: () -> Unit,
    onOpenProviderSubscription: (String, String) -> Unit,
    onReauthenticateProvider: (String) -> Unit,
    onCloseProviderSubscription: () -> Unit,
    onUpdateProviderTitle: (String) -> Unit,
    onSelectProviderKind: (String) -> Unit,
    onUpdateProviderSetting: (String, String?) -> Unit,
    onSubmitProviderSubscription: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (state.playlists.isEmpty() && destination != TvDestination.Status) {
            EmptyLibraryScreen()
        } else {
            when (destination) {
                TvDestination.Home -> HomeScreen(
                    state = state,
                    onOpenLibrary = onOpenLibrary,
                    onPlaylist = onPlaylist,
                    onPlay = onPlay,
                    onPlayRecent = onPlayRecent
                )

                TvDestination.Library -> LibraryScreen(
                    state = state,
                    onPlaylist = onPlaylist,
                    onRefresh = onRefresh,
                    onPlay = onPlay
                )

                TvDestination.Favorites -> ChannelGridScreen(
                    title = stringResource(string.tv_favorites_title),
                    subtitle = stringResource(string.tv_favorites_subtitle),
                    channels = state.favorites,
                    onPlay = onPlay
                )

                TvDestination.Status -> StatusScreen(
                    state = state,
                    onExternalExtensionsEnabled = onExternalExtensionsEnabled,
                    onEnableExtension = onEnableExtension,
                    onReauthorizeExtension = onReauthorizeExtension,
                    onDisableExtension = onDisableExtension,
                    onRevokeExtension = onRevokeExtension,
                    onClearExtensionData = onClearExtensionData,
                    onExportExtensionDiagnostics = onExportExtensionDiagnostics,
                    onOpenExtensionSettings = onOpenExtensionSettings,
                    onCloseExtensionSettings = onCloseExtensionSettings,
                    onUpdateExtensionSetting = onUpdateExtensionSetting,
                    onRefreshProviders = onRefreshProviders,
                    onOpenProviderSubscription = onOpenProviderSubscription,
                    onReauthenticateProvider = onReauthenticateProvider,
                    onCloseProviderSubscription = onCloseProviderSubscription,
                    onUpdateProviderTitle = onUpdateProviderTitle,
                    onSelectProviderKind = onSelectProviderKind,
                    onUpdateProviderSetting = onUpdateProviderSetting,
                    onSubmitProviderSubscription = onSubmitProviderSubscription,
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: TvUiState,
    onOpenLibrary: () -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onPlay: (Channel) -> Unit,
    onPlayRecent: () -> Unit
) {
    val featuredChannels = remember(state.recent, state.channels) {
        (listOfNotNull(state.recent) + state.channels)
            .distinctBy { it.id }
            .take(10)
    }
    var highlightedChannel by remember { mutableStateOf<Channel?>(null) }
    val activeChannel = highlightedChannel ?: featuredChannels.firstOrNull() ?: state.heroChannel
    val heroFocusRequester = remember { FocusRequester() }
    val firstFeaturedFocusRequester = remember { FocusRequester() }
    var initialFocusRequested by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        yield()
        if (!initialFocusRequested) {
            heroFocusRequester.requestFocus()
            initialFocusRequested = true
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(start = 48.dp, top = 48.dp, end = 64.dp, bottom = 24.dp),
        modifier = Modifier
            .fillMaxSize()
            .focusGroup()
    ) {
        item {
            FeaturedCarouselPane(
                state = state,
                channel = activeChannel,
                primaryFocusRequester = heroFocusRequester,
                nextFocusRequester = firstFeaturedFocusRequester,
                onOpenLibrary = onOpenLibrary,
                onPlayRecent = onPlayRecent,
                onPlay = onPlay
            )
        }
        if (featuredChannels.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle(
                        title = stringResource(string.tv_section_recent_channels),
                        subtitle = stringResource(string.tv_section_recent_channels_hint),
                        modifier = Modifier.padding(start = 48.dp)
                    )
                    ContentRow(
                        channels = featuredChannels,
                        onPlay = onPlay,
                        onFocused = { highlightedChannel = it },
                        firstItemFocusRequester = firstFeaturedFocusRequester
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(
                    title = stringResource(string.tv_section_playlists),
                    subtitle = stringResource(string.tv_section_playlists_hint),
                    modifier = Modifier.padding(start = 48.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(start = 48.dp, top = 16.dp, end = 48.dp, bottom = 8.dp),
                    modifier = Modifier.focusGroup()
                ) {
                    items(state.playlists, key = { it.url }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            count = state.counts[playlist] ?: 0,
                            selected = playlist == state.selectedPlaylist,
                            onClick = { onPlaylist(playlist) },
                            modifier = Modifier
                                .widthIn(min = 256.dp, max = 336.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedCarouselPane(
    state: TvUiState,
    channel: Channel?,
    primaryFocusRequester: FocusRequester,
    nextFocusRequester: FocusRequester,
    onOpenLibrary: () -> Unit,
    onPlayRecent: () -> Unit,
    onPlay: (Channel) -> Unit
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val largeTextLayout = tvLargeTextLayout(LocalDensity.current.fontScale)
    val primaryHeroAction = {
        if (channel == null) {
            onOpenLibrary()
        } else if (channel == state.recent) {
            onPlayRecent()
        } else {
            onPlay(channel)
        }
    }
    var selectedAction by remember(channel?.id) { mutableStateOf(TvHeroAction.PRIMARY) }
    val secondaryAvailable = channel != null
    val selectedHeroAction = if (secondaryAvailable) selectedAction else TvHeroAction.PRIMARY
    val openLibraryLabel = stringResource(string.tv_action_open_library)
    val primaryActionLabel = if (channel == null) {
        openLibraryLabel
    } else {
        stringResource(string.tv_action_resume)
    }

    FocusFrame(
        onClick = {
            when (selectedHeroAction) {
                TvHeroAction.PRIMARY -> primaryHeroAction()
                TvHeroAction.SECONDARY -> onOpenLibrary()
            }
        },
        shape = RoundedCornerShape(16.dp),
        focusRequester = primaryFocusRequester,
        focusedScale = 1f,
        focusedBorderWidth = 0.dp,
        focusedBorderColor = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = largeTextLayout.heroMinHeightDp.dp)
            .focusProperties { down = nextFocusRequester },
        onKey = { event ->
            if (event.type != KeyEventType.KeyDown || !secondaryAvailable) {
                false
            } else {
                when (event.key) {
                    Key.DirectionLeft -> {
                        tvHeroActionAfterHorizontalMove(
                            current = selectedHeroAction,
                            direction = TvHorizontalDirection.LEFT,
                            isRtl = isRtl,
                        )?.let { action ->
                            selectedAction = action
                            true
                        } ?: false
                    }
                    Key.DirectionRight -> {
                        tvHeroActionAfterHorizontalMove(
                            current = selectedHeroAction,
                            direction = TvHorizontalDirection.RIGHT,
                            isRtl = isRtl,
                        )?.let { action ->
                            selectedAction = action
                            true
                        } ?: false
                    }
                    else -> false
                }
            }
        }
    ) { heroFocused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TvColors.BackgroundSoft)
        ) {
            if (channel != null) {
                PosterArt(
                    model = channel.cover,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.36f))
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            *tvLeadingGradientColorStops(
                                isRtl = isRtl,
                                leading = Color.Black.copy(alpha = 0.92f),
                                middle = Color.Black.copy(alpha = 0.72f),
                                trailing = Color.Transparent,
                                middlePosition = 0.48f,
                            ).toTypedArray()
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 48.dp, top = 32.dp, end = 48.dp, bottom = 32.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(largeTextLayout.heroTextWidthFraction)
                ) {
                    Text(
                        text = channel?.title?.title() ?: stringResource(string.tv_home_title),
                        color = TvColors.TextPrimary,
                        fontSize = 38.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TvFonts.Body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = channel?.category?.takeIf { it.isNotBlank() }
                            ?: stringResource(string.tv_home_subtitle),
                        color = TvColors.TextSecondary,
                        fontSize = 17.sp,
                        lineHeight = 25.sp,
                        fontFamily = TvFonts.Body,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (channel == null) {
                            HeroActionChip(
                                text = openLibraryLabel,
                                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                selected = heroFocused,
                                expanded = heroFocused
                            )
                        } else {
                            HeroActionChip(
                                text = primaryActionLabel,
                                icon = Icons.Rounded.PlayArrow,
                                selected = heroFocused && selectedHeroAction == TvHeroAction.PRIMARY,
                                expanded = heroFocused && selectedHeroAction == TvHeroAction.PRIMARY
                            )
                            HeroActionChip(
                                text = openLibraryLabel,
                                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                selected = heroFocused && selectedHeroAction == TvHeroAction.SECONDARY,
                                expanded = heroFocused && selectedHeroAction == TvHeroAction.SECONDARY
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroActionChip(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    expanded: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (expanded) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) TvColors.Focus else TvColors.Surface.copy(alpha = 0.86f))
            .border(
                BorderStroke(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.08f)
                ),
                RoundedCornerShape(24.dp)
            )
            .padding(
                horizontal = if (expanded) 16.dp else 12.dp,
                vertical = 8.dp,
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) TvColors.OnFocus else TvColors.TextPrimary,
            modifier = Modifier.size(24.dp)
        )
        if (expanded) {
            Text(
                text = text,
                color = if (selected) TvColors.OnFocus else TvColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = TvFonts.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LibraryScreen(
    state: TvUiState,
    onPlaylist: (Playlist) -> Unit,
    onRefresh: () -> Unit,
    onPlay: (Channel) -> Unit
) {
    val playlistFocusRequester = remember { FocusRequester() }
    val focusTarget = state.selectedPlaylist ?: state.playlists.firstOrNull()
    var initialFocusRequested by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (focusTarget != null && !initialFocusRequested) {
            yield()
            playlistFocusRequester.requestFocus()
            initialFocusRequested = true
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(start = 48.dp, top = 48.dp, end = 64.dp, bottom = 48.dp),
        modifier = Modifier
            .fillMaxSize()
            .focusGroup()
    ) {
        item {
            SectionTitle(
                title = stringResource(string.tv_library_title),
                subtitle = stringResource(string.tv_library_subtitle)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp),
                modifier = Modifier.focusGroup()
            ) {
                items(state.playlists, key = { it.url }) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        count = state.counts[playlist] ?: 0,
                        selected = playlist == state.selectedPlaylist,
                        onClick = { onPlaylist(playlist) },
                        focusRequester = if (playlist.url == focusTarget?.url) playlistFocusRequester else null,
                        modifier = Modifier
                            .widthIn(min = 256.dp, max = 336.dp)
                    )
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.selectedPlaylist?.title?.title().orEmpty(),
                        color = TvColors.TextPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = TvFonts.Body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = pluralStringResource(
                            plurals.tv_channel_count,
                            state.channels.size,
                            state.channels.size,
                        ),
                        color = TvColors.TextSecondary,
                        fontSize = 14.sp,
                        fontFamily = TvFonts.Body,
                        maxLines = 1
                    )
                }
                TvActionButton(
                    text = stringResource(string.feat_setting_label_subscribe),
                    icon = Icons.Rounded.Refresh,
                    onClick = onRefresh
                )
            }
        }

        item {
            ChannelGrid(
                channels = state.channels,
                onPlay = onPlay,
                modifier = Modifier.height(620.dp)
            )
        }
    }
}

@Composable
private fun ChannelGridScreen(
    title: String,
    subtitle: String,
    channels: List<Channel>,
    onPlay: (Channel) -> Unit
) {
    val firstChannelFocusRequester = remember { FocusRequester() }

    LaunchedEffect(channels.size) {
        if (channels.isNotEmpty()) {
            yield()
            firstChannelFocusRequester.requestFocus()
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 48.dp, top = 48.dp, end = 64.dp, bottom = 48.dp)
            .focusGroup()
    ) {
        SectionTitle(title = title, subtitle = subtitle)
        ChannelGrid(
            channels = channels,
            onPlay = onPlay,
            firstItemFocusRequester = firstChannelFocusRequester
        )
    }
}

private sealed interface TvStatusReturnFocusTarget {
    data class Provider(
        val providerId: String,
        val providerKind: String,
    ) : TvStatusReturnFocusTarget

    data class Reauthentication(
        val playlistUrl: String,
        val providerId: String,
        val providerKind: String,
    ) : TvStatusReturnFocusTarget

    data class Plugin(
        val packageName: String,
        val serviceName: String,
        val action: TvExtensionPluginAction,
    ) : TvStatusReturnFocusTarget
}

@Composable
private fun StatusScreen(
    state: TvUiState,
    onExternalExtensionsEnabled: (Boolean) -> Unit,
    onEnableExtension: (String, String, PluginAuthorizationToken) -> Unit,
    onReauthorizeExtension: (String, String, PluginAuthorizationToken) -> Unit,
    onDisableExtension: (String) -> Unit,
    onRevokeExtension: (String, String, String?) -> Unit,
    onClearExtensionData: (String, String, String?) -> Unit,
    onExportExtensionDiagnostics: (String) -> Unit,
    onOpenExtensionSettings: (String) -> Unit,
    onCloseExtensionSettings: () -> Unit,
    onUpdateExtensionSetting: (String, String, ExtensionSettingEditToken, String?) -> Unit,
    onRefreshProviders: () -> Unit,
    onOpenProviderSubscription: (String, String) -> Unit,
    onReauthenticateProvider: (String) -> Unit,
    onCloseProviderSubscription: () -> Unit,
    onUpdateProviderTitle: (String) -> Unit,
    onSelectProviderKind: (String) -> Unit,
    onUpdateProviderSetting: (String, String?) -> Unit,
    onSubmitProviderSubscription: () -> Unit,
) {
    val bidiFormatter = rememberTvBidiFormatter()
    val density = LocalDensity.current
    val largeTextLayout = tvLargeTextLayout(density.fontScale)
    val focusRestoreOffsetPx = with(density) { 28.dp.roundToPx() }
    var pendingTrust by remember { mutableStateOf<InstalledPlugin?>(null) }
    var pendingReauthorization by remember { mutableStateOf(false) }
    var pendingRevoke by remember { mutableStateOf<InstalledPlugin?>(null) }
    var pendingClear by remember { mutableStateOf<InstalledPlugin?>(null) }
    val returnFocusRequester = remember { FocusRequester() }
    var returnFocusTarget by remember {
        mutableStateOf<TvStatusReturnFocusTarget?>(null)
    }
    var pluginPanelCancelled by remember { mutableStateOf(false) }
    var restoreWithoutPanelRequested by remember { mutableStateOf(false) }
    var panelWasVisible by remember { mutableStateOf(false) }
    val statusListState = rememberLazyListState()
    val extensionOperationFailedMessage =
        stringResource(string.feat_setting_extension_operation_failed)
    val panelIsVisible =
        pendingRevoke != null ||
            pendingClear != null ||
            pendingTrust != null ||
            state.extensionSettings != null ||
            state.providerSubscriptionForm != null
    val reauthenticationAccounts =
        state.providerAccounts.filter(ProviderAccountSummary::requiresReauthentication)
    val providerFeedbackVisible = state.providerSubscriptionFeedback != null
    val providerDiscoveryItemCount = when (val discovery = state.providerDiscoveryState) {
        ProviderDiscoveryState.Loading,
        ProviderDiscoveryState.Empty,
        -> 1

        is ProviderDiscoveryState.Failed -> 2
        is ProviderDiscoveryState.Ready -> discovery.providers.sumOf { provider ->
            provider.descriptor.variants.count { variant -> variant.userSelectable }
        }
    }
    val developerModeItemIndex = tvExtensionDeveloperModeItemIndex(
        providerFeedbackVisible = providerFeedbackVisible,
        reauthenticationCount = reauthenticationAccounts.size,
        providerDiscoveryItemCount = providerDiscoveryItemCount,
        extensionErrorVisible = state.extensionPluginOperationFailed,
    )
    val selectableProviderVariants =
        (state.providerDiscoveryState as? ProviderDiscoveryState.Ready)
            ?.providers
            ?.flatMap { provider ->
                provider.descriptor.variants
                    .filter { variant -> variant.userSelectable }
                    .map { variant ->
                        provider.descriptor.providerId.value to variant.kind.value
                    }
            }
            .orEmpty()
    fun providerVariantItemIndex(providerId: String, providerKind: String): Int? {
        val variantIndex = selectableProviderVariants.indexOfFirst { (id, kind) ->
            id == providerId && kind == providerKind
        }
        return variantIndex.takeIf { it >= 0 }?.let { index ->
            tvProviderVariantItemIndex(
                providerFeedbackVisible = providerFeedbackVisible,
                reauthenticationCount = reauthenticationAccounts.size,
                providerVariantIndex = index,
            )
        }
    }
    val reauthenticationReturnTarget =
        returnFocusTarget as? TvStatusReturnFocusTarget.Reauthentication
    val reauthenticationReturnFocusAnchor =
        reauthenticationReturnTarget?.let { target ->
            tvProviderReauthenticationFocusAnchor(
                subscriptionSucceeded =
                    state.providerSubscriptionFeedback is TvProviderSubscriptionFeedback.Added,
                accountActionVisible = reauthenticationAccounts.any { account ->
                    account.playlistUrl == target.playlistUrl
                },
            )
        }
    val providerReturnItemIndex = when (val target = returnFocusTarget) {
        is TvStatusReturnFocusTarget.Provider -> providerVariantItemIndex(
            providerId = target.providerId,
            providerKind = target.providerKind,
        )

        is TvStatusReturnFocusTarget.Reauthentication -> {
            if (
                reauthenticationReturnFocusAnchor ==
                TvProviderReauthenticationFocusAnchor.ACCOUNT_ACTION
            ) {
                val reauthenticationIndex =
                    reauthenticationAccounts.indexOfFirst { account ->
                        account.playlistUrl == target.playlistUrl
                    }
                tvProviderReauthenticationItemIndex(
                    providerFeedbackVisible = providerFeedbackVisible,
                    reauthenticationIndex = reauthenticationIndex,
                )
            } else {
                providerVariantItemIndex(
                    providerId = target.providerId,
                    providerKind = target.providerKind,
                )
            }
        }

        else -> null
    }
    val pluginReturnTarget =
        returnFocusTarget as? TvStatusReturnFocusTarget.Plugin
    val pluginReturnPluginIndex =
        pluginReturnTarget
            ?.let { target ->
                state.extensionPlugins.indexOfFirst { candidate ->
                    candidate.packageName == target.packageName &&
                        candidate.serviceName == target.serviceName
                }
            }
            ?.takeIf { pluginIndex -> pluginIndex >= 0 }
    val pluginReturnPlugin =
        pluginReturnPluginIndex?.let(state.extensionPlugins::get)
    val pluginReturnItemIndex =
        pluginReturnPluginIndex?.let { pluginIndex ->
            developerModeItemIndex + 1 + pluginIndex
        }
    val pluginReturnSourceActionAvailable =
        pluginReturnTarget?.let { target ->
            pluginReturnPlugin
                ?.tvActionAvailability()
                ?.isActionAvailable(target.action)
        } == true

    LaunchedEffect(
        panelIsVisible,
        restoreWithoutPanelRequested,
        returnFocusTarget,
        pluginPanelCancelled,
        developerModeItemIndex,
        providerReturnItemIndex,
        pluginReturnItemIndex,
        pluginReturnSourceActionAvailable,
    ) {
        val shouldRestoreAfterPanel = shouldRestoreTvStatusFocus(
            panelWasVisible = panelWasVisible,
            panelIsVisible = panelIsVisible,
            hasReturnTarget = returnFocusTarget != null,
        )
        if (
            shouldRestoreAfterPanel ||
            (!panelIsVisible && restoreWithoutPanelRequested)
        ) {
            restoreWithoutPanelRequested = true
            if (providerReturnItemIndex != null) {
                statusListState.scrollToItem(
                    providerReturnItemIndex,
                    scrollOffset = -focusRestoreOffsetPx,
                )
            } else {
                val pluginTarget =
                    returnFocusTarget as? TvStatusReturnFocusTarget.Plugin
                if (pluginTarget != null) {
                    when (tvExtensionPluginReturnFocusAnchor(
                        action = pluginTarget.action,
                        panelCancelled = pluginPanelCancelled,
                    )) {
                        TvExtensionPluginReturnFocusAnchor.DEVELOPER_MODE ->
                            statusListState.scrollToItem(
                                developerModeItemIndex,
                                scrollOffset = -focusRestoreOffsetPx,
                            )

                        TvExtensionPluginReturnFocusAnchor.SOURCE_ACTION ->
                            pluginReturnItemIndex?.let { itemIndex ->
                                statusListState.scrollToItem(
                                    itemIndex,
                                    scrollOffset = -focusRestoreOffsetPx,
                                )
                            }
                    }
                }
            }
            repeat(2) { withFrameNanos { } }
            var restored = false
            repeat(3) {
                if (!restored) {
                    restored = runCatching {
                        returnFocusRequester.requestFocus()
                    }.getOrDefault(false)
                    if (!restored) withFrameNanos { }
                }
            }
            if (restored) {
                restoreWithoutPanelRequested = false
                returnFocusTarget = null
                pluginPanelCancelled = false
            }
        }
        panelWasVisible = panelIsVisible
    }
    LaunchedEffect(state.externalExtensionsEnabled) {
        if (!state.externalExtensionsEnabled) {
            pendingTrust = null
            pendingReauthorization = false
            pendingRevoke = null
            pendingClear = null
            returnFocusTarget = null
            pluginPanelCancelled = false
            onCloseExtensionSettings()
        }
    }

    pendingRevoke?.let { plugin ->
        ExtensionForgetConfirmation(
            plugin = plugin,
            onConfirm = {
                pluginPanelCancelled = false
                pendingRevoke = null
                onRevokeExtension(
                    plugin.packageName,
                    plugin.serviceName,
                    plugin.extensionId,
                )
            },
            onCancel = {
                pluginPanelCancelled = true
                pendingRevoke = null
            },
        )
        return
    }

    pendingClear?.let { plugin ->
        ExtensionClearConfirmation(
            plugin = plugin,
            onConfirm = {
                pluginPanelCancelled = false
                pendingClear = null
                onClearExtensionData(
                    plugin.packageName,
                    plugin.serviceName,
                    plugin.extensionId,
                )
            },
            onCancel = {
                pluginPanelCancelled = true
                pendingClear = null
            },
        )
        return
    }

    state.extensionSettings?.let { configuration ->
        ExtensionSettingsPanel(
            configuration = configuration,
            operationError = extensionOperationFailedMessage.takeIf {
                state.extensionPluginOperationFailed
            },
            onClose = onCloseExtensionSettings,
            onUpdate = onUpdateExtensionSetting,
        )
        return
    }

    state.providerSubscriptionForm?.let { form ->
        val descriptor = state.providerSubscriptionDescriptor
            ?.takeIf { candidate -> candidate.providerId == form.providerId }
        val providerAvailability = tvProviderFormAvailability(
            discoveryLoading = state.providerDiscoveryState is ProviderDiscoveryState.Loading,
            providerSupported = state.providerDiscoveryState.supports(form),
            providerMarkedUnavailable = state.providerSubscriptionUnavailable,
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(start = 48.dp, top = 48.dp, end = 64.dp, bottom = 48.dp),
            modifier = Modifier.fillMaxSize().focusGroup(),
        ) {
            item {
                ProviderSubscriptionPanel(
                    form = form,
                    providerName = descriptor?.displayName
                        ?: form.providerId.value,
                    variants = descriptor?.variants
                        ?.filter { variant ->
                            variant.userSelectable || variant.kind == form.providerKind
                        }
                        ?.map { variant ->
                            variant.kind.value to variant.displayName
                        }
                        .orEmpty()
                        .ifEmpty {
                            listOf(form.providerKind.value to form.providerKind.value)
                    },
                    title = state.providerSubscriptionTitle,
                    inProgress = state.providerSubscriptionInProgress,
                    providerAvailability = providerAvailability,
                    feedback = state.providerSubscriptionFeedback,
                    onClose = onCloseProviderSubscription,
                    onTitleChange = onUpdateProviderTitle,
                    onKind = onSelectProviderKind,
                    onSetting = onUpdateProviderSetting,
                    onRetry = onRefreshProviders,
                    onSubmit = onSubmitProviderSubscription,
                )
            }
        }
        return
    }

    pendingTrust?.takeIf { state.externalExtensionsEnabled }?.let { plugin ->
        ExtensionAuthorizationConfirmation(
            plugin = plugin,
            reauthorization = pendingReauthorization,
            onConfirm = {
                pluginPanelCancelled = false
                val reauthorize = pendingReauthorization
                pendingTrust = null
                pendingReauthorization = false
                plugin.authorizationToken?.let { authorizationToken ->
                    if (reauthorize) {
                        onReauthorizeExtension(
                            plugin.packageName,
                            plugin.serviceName,
                            authorizationToken,
                        )
                    } else {
                        onEnableExtension(
                            plugin.packageName,
                            plugin.serviceName,
                            authorizationToken,
                        )
                    }
                }
            },
            onCancel = {
                pluginPanelCancelled = true
                pendingTrust = null
                pendingReauthorization = false
            },
        )
        return
    }

    LazyColumn(
        state = statusListState,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(start = 48.dp, top = 48.dp, end = 64.dp, bottom = 48.dp),
        modifier = Modifier.fillMaxSize().focusGroup(),
    ) {
        item {
            SectionTitle(
                title = stringResource(string.tv_settings_title),
                subtitle = stringResource(string.tv_settings_subtitle)
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
            MetricTile(
                title = stringResource(string.tv_metric_playlists),
                value = state.playlists.size.toString(),
                icon = Icons.Rounded.VideoLibrary,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = largeTextLayout.metricTileMinHeightDp.dp)
            )
            MetricTile(
                title = stringResource(string.tv_metric_channels),
                value = state.channelCount.toString(),
                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = largeTextLayout.metricTileMinHeightDp.dp)
            )
            MetricTile(
                title = stringResource(string.tv_metric_favorites),
                value = state.favorites.size.toString(),
                icon = Icons.Rounded.Favorite,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = largeTextLayout.metricTileMinHeightDp.dp)
            )
        }
        }
        item {
            SectionTitle(
                title = stringResource(string.feat_setting_data_source_provider),
                subtitle = stringResource(string.feat_setting_playlist_management),
            )
        }
        state.providerSubscriptionFeedback?.let { feedback ->
            item {
                ProviderSubscriptionFeedback(feedback)
            }
        }
        items(
            items = reauthenticationAccounts,
            key = { account -> "reauth:${account.playlistUrl}" },
        ) { account ->
            val focusTarget = TvStatusReturnFocusTarget.Reauthentication(
                playlistUrl = account.playlistUrl,
                providerId = account.providerId.value,
                providerKind = account.providerKind.value,
            )
            ProviderReauthenticationCard(
                account = account,
                focusRequester = returnFocusRequester.takeIf {
                    returnFocusTarget == focusTarget &&
                        reauthenticationReturnFocusAnchor ==
                        TvProviderReauthenticationFocusAnchor.ACCOUNT_ACTION
                },
                onReauthenticate = {
                    returnFocusTarget = focusTarget
                    onReauthenticateProvider(account.playlistUrl)
                },
            )
        }
        when (val discovery = state.providerDiscoveryState) {
            ProviderDiscoveryState.Loading -> item {
                Text(
                    stringResource(string.feat_setting_provider_discovery_loading),
                    color = TvColors.TextSecondary,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }

            ProviderDiscoveryState.Empty -> item {
                Text(
                    stringResource(string.feat_setting_provider_discovery_empty),
                    color = TvColors.TextSecondary,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }

            is ProviderDiscoveryState.Failed -> {
                item {
                    val message =
                        stringResource(string.feat_setting_provider_discovery_failed)
                    Text(
                        message,
                        color = TvColors.Danger,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                }
                item {
                    TvActionButton(
                        text = stringResource(string.feat_setting_provider_discovery_retry),
                        icon = Icons.Rounded.Refresh,
                        onClick = onRefreshProviders,
                    )
                }
            }

            is ProviderDiscoveryState.Ready -> {
                discovery.providers.forEach { provider ->
                    val selectableVariants = provider.descriptor.variants.filter { variant ->
                        variant.userSelectable
                    }
                    selectableVariants.forEach { variant ->
                        item(key = "provider:${provider.descriptor.providerId.value}:${variant.kind.value}") {
                            val isExternal =
                                provider.executionKind ==
                                    SubscriptionProviderExecutionKind.EXTERNAL
                            val presentation = tvProviderChoicePresentation(
                                providerId = provider.descriptor.providerId.value,
                                providerDisplayName = provider.descriptor.displayName,
                                variantDisplayName = variant.displayName,
                                external = isExternal,
                            )
                            val visualVariantName = bidiFormatter.natural(
                                presentation.variantName,
                            )
                            val visualProviderLabel =
                                presentation.providerName?.let { providerName ->
                                    stringResource(
                                        string.feat_setting_provider_source_with_provider,
                                        visualVariantName,
                                        bidiFormatter.natural(providerName),
                                    )
                                } ?: visualVariantName
                            val semanticProviderLabel =
                                presentation.providerName?.let { providerName ->
                                    stringResource(
                                        string.feat_setting_provider_source_with_provider,
                                        bidiFormatter.natural(presentation.variantName),
                                        bidiFormatter.natural(providerName),
                                    )
                                } ?: bidiFormatter.natural(presentation.variantName)
                            val visualProviderId = bidiFormatter.ltr(
                                provider.descriptor.providerId.value,
                            )
                            val semanticProviderId = bidiFormatter.ltr(
                                provider.descriptor.providerId.value,
                            )
                            val focusTarget = TvStatusReturnFocusTarget.Provider(
                                providerId = provider.descriptor.providerId.value,
                                providerKind = variant.kind.value,
                            )
                            val reauthenticationTarget = returnFocusTarget
                                as? TvStatusReturnFocusTarget.Reauthentication
                            val restoresReauthentication =
                                reauthenticationTarget?.let { target ->
                                    target.providerId ==
                                        provider.descriptor.providerId.value &&
                                        target.providerKind == variant.kind.value
                                } == true &&
                                    reauthenticationReturnFocusAnchor ==
                                    TvProviderReauthenticationFocusAnchor.PROVIDER_VARIANT
                            TvActionButton(
                                text = visualProviderLabel,
                                supportingText = visualProviderId.takeIf { isExternal },
                                icon = Icons.Rounded.Extension,
                                semanticsLabel = if (isExternal) {
                                    stringResource(
                                        string.feat_setting_provider_choice_with_identifier_description,
                                        semanticProviderLabel,
                                        semanticProviderId,
                                    )
                                } else {
                                    semanticProviderLabel
                                },
                                focusRequester = returnFocusRequester.takeIf {
                                    returnFocusTarget == focusTarget ||
                                        restoresReauthentication
                                },
                                onClick = {
                                    returnFocusTarget = focusTarget
                                    onOpenProviderSubscription(
                                        provider.descriptor.providerId.value,
                                        variant.kind.value,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        item {
            SectionTitle(
                title = stringResource(string.feat_setting_extension_plugins),
                subtitle = stringResource(string.tv_extensions_subtitle),
            )
        }
        if (state.extensionPluginOperationFailed) {
            item {
                Text(
                    extensionOperationFailedMessage,
                    color = TvColors.Danger,
                    fontSize = 16.sp,
                    modifier = Modifier.semantics {
                        error(extensionOperationFailedMessage)
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }
        }
        item(key = "extensions:developer-mode") {
            val pluginFocusTarget =
                returnFocusTarget as? TvStatusReturnFocusTarget.Plugin
            val restoresPluginMutation = pluginFocusTarget?.let { target ->
                tvExtensionPluginReturnFocusAnchor(
                    action = target.action,
                    panelCancelled = pluginPanelCancelled,
                ) ==
                    TvExtensionPluginReturnFocusAnchor.DEVELOPER_MODE
            } == true
            TvActionButton(
                text = stringResource(
                    if (state.externalExtensionsEnabled) {
                        string.tv_extensions_disable_developer_mode
                    } else {
                        string.tv_extensions_enable_developer_mode
                    }
                ),
                icon = if (state.externalExtensionsEnabled) Icons.Rounded.Block else Icons.Rounded.Extension,
                checked = state.externalExtensionsEnabled,
                semanticRole = Role.Switch,
                semanticsLabel = stringResource(string.feat_setting_external_extensions),
                focusRequester = returnFocusRequester.takeIf {
                    restoresPluginMutation
                },
                onClick = { onExternalExtensionsEnabled(!state.externalExtensionsEnabled) },
            )
        }
        if (state.externalExtensionsEnabled && state.extensionPlugins.isEmpty()) {
            item { Text(stringResource(string.feat_setting_extension_no_plugins), color = TvColors.TextSecondary) }
        }
        if (state.externalExtensionsEnabled) {
            items(
                items = state.extensionPlugins,
                key = { plugin -> "${plugin.packageName}/${plugin.serviceName}" },
            ) { plugin ->
                val pluginFocusTarget = (returnFocusTarget as? TvStatusReturnFocusTarget.Plugin)
                    ?.takeIf { target ->
                        target.packageName == plugin.packageName &&
                            target.serviceName == plugin.serviceName
                    }
                fun setPluginReturnFocus(action: TvExtensionPluginAction) {
                    pluginPanelCancelled = false
                    returnFocusTarget = TvStatusReturnFocusTarget.Plugin(
                        packageName = plugin.packageName,
                        serviceName = plugin.serviceName,
                        action = action,
                    )
                }
                val restoresSourceAction = pluginFocusTarget?.let { target ->
                    tvExtensionPluginReturnFocusAnchor(
                        action = target.action,
                        panelCancelled = pluginPanelCancelled,
                    ) ==
                        TvExtensionPluginReturnFocusAnchor.SOURCE_ACTION
                } == true
                ExtensionPluginCard(
                    plugin = plugin,
                    restoreFocusAction = pluginFocusTarget?.action.takeIf {
                        restoresSourceAction
                    },
                    restoreFocusRequester = returnFocusRequester.takeIf {
                        restoresSourceAction
                    },
                    onEnable = {
                        setPluginReturnFocus(TvExtensionPluginAction.ENABLE)
                        pendingTrust = plugin
                    },
                    onReauthorize = {
                        setPluginReturnFocus(TvExtensionPluginAction.REAUTHORIZE)
                        pendingReauthorization = true
                        pendingTrust = plugin
                    },
                    onDisable = {
                        setPluginReturnFocus(TvExtensionPluginAction.DISABLE)
                        restoreWithoutPanelRequested = true
                        plugin.extensionId?.let(onDisableExtension)
                    },
                    onRevoke = {
                        setPluginReturnFocus(TvExtensionPluginAction.REVOKE)
                        pendingRevoke = plugin
                    },
                    onOpenSettings = {
                        setPluginReturnFocus(TvExtensionPluginAction.SETTINGS)
                        plugin.extensionId?.let(onOpenExtensionSettings)
                    },
                    onClearData = {
                        setPluginReturnFocus(TvExtensionPluginAction.CLEAR_DATA)
                        pendingClear = plugin
                    },
                    onExportDiagnostics = {
                        plugin.extensionId?.let(onExportExtensionDiagnostics)
                    },
                )
            }
        }
    }
}

@Composable
private fun ProviderSubscriptionPanel(
    form: ProviderSubscriptionForm,
    providerName: String,
    variants: List<Pair<String, String>>,
    title: String,
    inProgress: Boolean,
    providerAvailability: TvProviderFormAvailability,
    feedback: TvProviderSubscriptionFeedback?,
    onClose: () -> Unit,
    onTitleChange: (String) -> Unit,
    onKind: (String) -> Unit,
    onSetting: (String, String?) -> Unit,
    onRetry: () -> Unit,
    onSubmit: () -> Unit,
) {
    val bidiFormatter = rememberTvBidiFormatter()
    val initialFocusRequester = remember { FocusRequester() }
    val titleField = ExtensionSettingField(
        key = "playlist_title",
        label = stringResource(string.feat_setting_placeholder_title),
        type = ExtensionSettingType.TEXT,
        required = true,
    )
    LaunchedEffect(form.providerId, form.providerKind) {
        repeat(2) { withFrameNanos { } }
        initialFocusRequester.requestFocus()
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = bidiFormatter.natural(providerName),
                    color = TvColors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = variants.firstOrNull { (kind, _) -> kind == form.providerKind.value }
                        ?.second
                        ?.let(bidiFormatter::natural)
                        .orEmpty(),
                    color = TvColors.TextSecondary,
                    fontSize = 14.sp,
                )
            }
            TvActionButton(
                text = stringResource(android.R.string.cancel),
                icon = Icons.Rounded.Block,
                enabled = !inProgress,
                onClick = onClose,
            )
        }
        if (variants.size > 1) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.selectableGroup(),
            ) {
                variants.forEach { (kind, label) ->
                    TvActionButton(
                        text = bidiFormatter.natural(label),
                        icon = if (kind == form.providerKind.value) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.Extension
                        },
                        enabled = !inProgress &&
                            providerAvailability == TvProviderFormAvailability.AVAILABLE,
                        selected = kind == form.providerKind.value,
                        semanticRole = Role.RadioButton,
                        semanticsLabel = bidiFormatter.natural(label),
                        onClick = { onKind(kind) },
                    )
                }
            }
        }
        val titleError = if (
            feedback == TvProviderSubscriptionFeedback.InvalidSettings && title.isBlank()
        ) {
            providerFieldErrorMessage(ProviderSettingFieldError.REQUIRED)
        } else {
            null
        }
        TvExtensionSettingControl(
            field = titleField,
            rawValue = title,
            secretConfigured = false,
            focusRequester = initialFocusRequester,
            accessibilityError = titleError,
            onDraftChange = onTitleChange,
            onUpdate = { value -> onTitleChange(value.orEmpty()) },
        )
        titleError?.let { ProviderFieldError(it) }
        form.fields.forEach { field ->
            val fieldError = field.error?.let { providerFieldErrorMessage(it) }
            TvExtensionSettingControl(
                field = field.definition,
                rawValue = field.input ?: field.value.orEmpty(),
                secretConfigured = false,
                focusRequester = null,
                accessibilityError = fieldError,
                onDraftChange = { value -> onSetting(field.definition.key, value) },
                onUpdate = { value -> onSetting(field.definition.key, value) },
            )
            if (field.isUsingDefault) {
                Text(
                    text = stringResource(string.feat_setting_provider_value_default),
                    color = TvColors.TextSecondary,
                    fontSize = 14.sp,
                )
            }
            fieldError?.let { ProviderFieldError(it) }
        }
        when (providerAvailability) {
            TvProviderFormAvailability.AVAILABLE -> Unit

            TvProviderFormAvailability.LOADING -> {
                Text(
                    text = stringResource(string.feat_setting_provider_discovery_loading),
                    color = TvColors.TextSecondary,
                    fontSize = 16.sp,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }

            TvProviderFormAvailability.UNAVAILABLE -> {
                Text(
                    text = stringResource(string.feat_setting_provider_selected_unavailable),
                    color = TvColors.Danger,
                    fontSize = 16.sp,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                )
                TvActionButton(
                    text = stringResource(string.feat_setting_provider_discovery_retry),
                    icon = Icons.Rounded.Refresh,
                    enabled = !inProgress,
                    onClick = onRetry,
                )
            }
        }
        feedback?.let { ProviderSubscriptionFeedback(it) }
        TvActionButton(
            text = stringResource(
                if (inProgress) {
                    string.feat_setting_label_subscribing
                } else {
                    string.feat_setting_label_subscribe
                }
            ),
            icon = Icons.Rounded.CheckCircle,
            enabled = tvProviderSubmitEnabled(inProgress, providerAvailability),
            focusableWhenDisabled = inProgress,
            onClick = onSubmit,
        )
    }
}

@Composable
private fun ProviderReauthenticationCard(
    account: ProviderAccountSummary,
    focusRequester: FocusRequester?,
    onReauthenticate: () -> Unit,
) {
    val bidiFormatter = rememberTvBidiFormatter()
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TvColors.Danger, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(
                string.feat_setting_provider_reauthentication_required,
                bidiFormatter.natural(account.playlistTitle),
            ),
            color = TvColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(
                string.feat_setting_provider_account_summary,
                bidiFormatter.natural(account.serverName),
                bidiFormatter.natural(account.username),
                bidiFormatter.ltr(account.baseUrl),
            ),
            color = TvColors.TextSecondary,
            fontSize = 14.sp,
        )
        if (account.requiresExtensionOwnerConfirmation) {
            Text(
                text = stringResource(string.feat_setting_provider_owner_claim_notice),
                color = TvColors.TextSecondary,
                fontSize = 14.sp,
            )
        }
        TvActionButton(
            text = stringResource(string.feat_setting_provider_reauthenticate),
            icon = Icons.Rounded.Refresh,
            focusRequester = focusRequester,
            onClick = onReauthenticate,
        )
    }
}

@Composable
private fun ProviderSubscriptionFeedback(feedback: TvProviderSubscriptionFeedback) {
    val (text, color) = when (feedback) {
        TvProviderSubscriptionFeedback.InvalidSettings ->
            stringResource(string.feat_setting_provider_credentials_required) to TvColors.Danger

        TvProviderSubscriptionFeedback.Failed ->
            stringResource(string.feat_setting_provider_subscription_failed) to TvColors.Danger

        is TvProviderSubscriptionFeedback.Added ->
            stringResource(string.feat_setting_provider_added) to TvColors.Focus
    }
    Text(
        text = text,
        color = color,
        fontSize = 16.sp,
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
        },
    )
}

@Composable
private fun providerFieldErrorMessage(error: ProviderSettingFieldError): String =
    stringResource(
        when (error) {
            ProviderSettingFieldError.REQUIRED -> string.feat_setting_provider_error_required
            ProviderSettingFieldError.TOO_LONG -> string.feat_setting_provider_error_too_long
            ProviderSettingFieldError.INVALID_NUMBER -> string.feat_setting_provider_error_number
            ProviderSettingFieldError.INVALID_BOOLEAN -> string.feat_setting_provider_error_boolean
            ProviderSettingFieldError.INVALID_CHOICE -> string.feat_setting_provider_error_choice
            ProviderSettingFieldError.UNSAFE_VALUE ->
                string.feat_setting_provider_error_unsafe_value
        }
    )

@Composable
private fun extensionSettingFieldErrorMessage(error: ExtensionSettingInputError): String =
    stringResource(
        when (error) {
            ExtensionSettingInputError.REQUIRED -> string.feat_setting_provider_error_required
            ExtensionSettingInputError.TOO_LONG -> string.feat_setting_provider_error_too_long
            ExtensionSettingInputError.INVALID_NUMBER -> string.feat_setting_provider_error_number
            ExtensionSettingInputError.INVALID_BOOLEAN ->
                string.feat_setting_provider_error_boolean
            ExtensionSettingInputError.INVALID_CHOICE -> string.feat_setting_provider_error_choice
            ExtensionSettingInputError.INVALID_NETWORK_ORIGIN ->
                string.feat_setting_extension_error_network_origin
        }
    )

@Composable
private fun ProviderFieldError(message: String) {
    Text(
        text = message,
        color = TvColors.Danger,
        fontSize = 14.sp,
        modifier = Modifier.semantics {
            error(message)
            liveRegion = LiveRegionMode.Polite
        },
    )
}

@Composable
private fun ExtensionAuthorizationConfirmation(
    plugin: InstalledPlugin,
    reauthorization: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val bidiFormatter = rememberTvBidiFormatter()
    val cancelFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val identityText = stringResource(
        string.feat_setting_extension_confirm_identity,
        bidiFormatter.ltr(plugin.packageName),
        bidiFormatter.ltr(
            plugin.certificateSha256.chunked(16).joinToString(" ")
        ),
        bidiFormatter.natural(plugin.displayName.orEmpty()),
        bidiFormatter.natural(plugin.developer.orEmpty()),
        bidiFormatter.ltr(plugin.version.orEmpty()),
    )
    val identitySegments = remember(identityText) {
        identityText.lines()
    }
    val certificateRepinText = plugin.previousCertificateSha256?.let { previousCertificate ->
        stringResource(
            string.feat_setting_extension_certificate_repin,
            bidiFormatter.ltr(previousCertificate.chunked(16).joinToString(" ")),
            bidiFormatter.ltr(
                plugin.certificateSha256.chunked(16).joinToString(" ")
            ),
        )
    }
    val certificateRepinSegments = remember(certificateRepinText) {
        certificateRepinText
            ?.lines()
            .orEmpty()
    }
    val requiredCapabilityLabel =
        stringResource(string.feat_setting_extension_capability_required)
    val optionalCapabilityLabel =
        stringResource(string.feat_setting_extension_capability_optional)
    val capabilityTitles = plugin.capabilityPermissions.associate { permission ->
        val capabilityName = tvExtensionCapabilityNameResource(permission.id)
            ?.let { resource -> stringResource(resource) }
            ?.let(bidiFormatter::natural)
            ?: bidiFormatter.ltr(permission.id)
        val requirement = if (permission.required) {
            requiredCapabilityLabel
        } else {
            optionalCapabilityLabel
        }
        permission.id to "$capabilityName ($requirement)"
    }
    val capabilitySegments = remember(
        plugin.capabilityPermissions,
        capabilityTitles,
        bidiFormatter,
    ) {
        plugin.capabilityPermissions.flatMap { permission ->
            val title = checkNotNull(capabilityTitles[permission.id])
            permission.reason.tvReadableSegments().map { reason ->
                title to bidiFormatter.natural(reason)
            }
        }
    }
    val networkOriginSegments = remember(plugin.networkOrigins, bidiFormatter) {
        plugin.networkOrigins.sorted().flatMap { origin ->
            origin.tvReadableSegments().map(bidiFormatter::ltr)
        }
    }
    val networkOriginSettingsText =
        if (plugin.networkOriginSettingFields.isNotEmpty()) {
            stringResource(
                string.feat_setting_extension_network_origin_settings,
                plugin.networkOriginSettingFields.sorted()
                    .joinToString(transform = bidiFormatter::ltr),
            )
        } else {
            null
        }
    val networkOriginSettingsSegments = remember(
        networkOriginSettingsText,
        bidiFormatter,
    ) {
        networkOriginSettingsText?.let(::listOf).orEmpty()
    }
    BackHandler(onBack = onCancel)
    LaunchedEffect(
        plugin.packageName,
        plugin.serviceName,
        plugin.certificateSha256,
        reauthorization,
    ) {
        listState.scrollToItem(0)
        repeat(2) { withFrameNanos { } }
        cancelFocusRequester.requestFocus()
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(start = 48.dp, top = 48.dp, end = 64.dp, bottom = 48.dp),
        modifier = Modifier.fillMaxSize().focusGroup(),
    ) {
        item {
            TvReadableConfirmationText(
                text = stringResource(string.feat_setting_extension_confirm_title),
                color = TvColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            TvActionButton(
                text = stringResource(android.R.string.cancel),
                icon = Icons.Rounded.Block,
                focusRequester = cancelFocusRequester,
                onClick = onCancel,
            )
        }
        items(identitySegments) { identitySegment ->
            TvReadableConfirmationText(
                text = identitySegment,
                color = TvColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        items(certificateRepinSegments) { certificateSegment ->
            TvReadableConfirmationText(
                text = certificateSegment,
                color = TvColors.Danger,
                fontSize = 14.sp,
            )
        }
        item {
            TvReadableConfirmationText(
                text = stringResource(string.feat_setting_extension_requested_capabilities),
                color = TvColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (plugin.capabilityPermissions.isEmpty()) {
            item {
                TvReadableConfirmationText(
                    text = "—",
                    color = TvColors.TextSecondary,
                    fontSize = 16.sp,
                )
            }
        } else {
            items(capabilitySegments) { (title, reason) ->
                TvReadableConfirmationBlock {
                    Text(
                        text = title,
                        color = TvColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        reason,
                        color = TvColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                }
            }
        }
        item {
            TvReadableConfirmationText(
                text = stringResource(string.feat_setting_extension_network_origins),
                color = TvColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (plugin.networkOrigins.isEmpty()) {
            item {
                TvReadableConfirmationText(
                    text = "—",
                    color = TvColors.TextSecondary,
                    fontSize = 16.sp,
                )
            }
        } else {
            items(networkOriginSegments) { originSegment ->
                TvReadableConfirmationText(
                    text = originSegment,
                    color = TvColors.TextSecondary,
                    fontSize = 16.sp,
                )
            }
        }
        items(networkOriginSettingsSegments) { settingsSegment ->
            TvReadableConfirmationText(
                text = settingsSegment,
                color = TvColors.TextSecondary,
                fontSize = 14.sp,
            )
        }
        item {
            TvActionButton(
                text = stringResource(
                    if (reauthorization) {
                        string.feat_setting_extension_reauthorize
                    } else {
                        string.feat_setting_extension_enable
                    }
                ),
                icon = Icons.Rounded.CheckCircle,
                onClick = onConfirm,
            )
        }
    }
}

@Composable
private fun extensionStateLabel(state: ExtensionState): String = stringResource(
    when (state) {
        ExtensionState.ENABLED -> string.feat_setting_extension_state_enabled
        ExtensionState.DISABLED -> string.feat_setting_extension_state_disabled
        ExtensionState.INCOMPATIBLE -> string.feat_setting_extension_state_incompatible
        ExtensionState.UNHEALTHY -> string.feat_setting_extension_state_unhealthy
    }
)

private fun InstalledPlugin.tvActionAvailability() =
    extensionPluginActionAvailability(
        enabled = enabled,
        state = state,
        hasExtensionId = extensionId != null,
        installed = installed,
        signatureChanged = signatureChanged,
        hasInspectionError = inspectionError != null,
        hasAuthorizationToken = authorizationToken != null,
        trusted = trusted,
        canClearData = canClearData,
    )

@Composable
private fun ExtensionPluginCard(
    plugin: InstalledPlugin,
    restoreFocusAction: TvExtensionPluginAction?,
    restoreFocusRequester: FocusRequester?,
    onEnable: () -> Unit,
    onReauthorize: () -> Unit,
    onDisable: () -> Unit,
    onRevoke: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearData: () -> Unit,
    onExportDiagnostics: () -> Unit,
) {
    val bidiFormatter = rememberTvBidiFormatter()
    val actions = plugin.tvActionAvailability()
    val semanticPluginName = plugin.displayName?.let(bidiFormatter::natural)
        ?: bidiFormatter.ltr(plugin.packageName)
    val settingsActionLabel = stringResource(string.feat_setting_extension_settings)
    val disableActionLabel = stringResource(string.feat_setting_extension_disable)
    val enableActionLabel = stringResource(string.feat_setting_extension_enable)
    val revokeActionLabel = stringResource(string.feat_setting_extension_revoke)
    val reauthorizeActionLabel = stringResource(string.feat_setting_extension_reauthorize)
    val exportDiagnosticsActionLabel =
        stringResource(string.feat_setting_extension_export_diagnostics)
    val clearDataActionLabel = stringResource(string.feat_setting_extension_clear_data)
    val context = LocalContext.current
    fun actionDescription(action: String): String = context.getString(
        string.feat_setting_extension_action_field_description,
        action,
        semanticPluginName,
    )
    val focusAction = restoreFocusAction?.takeIf(actions::isActionAvailable)
    fun focusRequesterFor(action: TvExtensionPluginAction): FocusRequester? =
        restoreFocusRequester.takeIf { focusAction == action }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = plugin.displayName?.let(bidiFormatter::natural)
                ?: bidiFormatter.ltr(plugin.packageName),
            color = TvColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(
                plugin.developer?.let(bidiFormatter::natural),
                plugin.version?.let { bidiFormatter.ltr("v$it") },
            ).joinToString(" · "),
            color = TvColors.TextSecondary,
            fontSize = 14.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = extensionStateLabel(plugin.state),
            color = TvColors.TextSecondary,
            fontSize = 14.sp,
            maxLines = 1,
        )
        Text(
            bidiFormatter.ltr(plugin.certificateSha256),
            color = TvColors.TextMuted,
            fontSize = 12.sp,
        )
        if (plugin.signatureChanged) {
            TvExtensionPluginErrorText(
                stringResource(string.feat_setting_extension_signature_changed)
            )
        }
        val unapprovedNetworkOrigins = plugin.networkOrigins - plugin.approvedNetworkOrigins
        if (plugin.trusted && unapprovedNetworkOrigins.isNotEmpty()) {
            TvExtensionPluginErrorText(
                stringResource(
                    string.feat_setting_extension_network_reauthorization_required,
                    unapprovedNetworkOrigins.sorted()
                        .joinToString(transform = bidiFormatter::ltr),
                )
            )
        }
        plugin.inspectionError?.let {
            TvExtensionPluginErrorText(
                stringResource(string.feat_setting_extension_inspection_failed)
            )
        }
        if (!plugin.installed) {
            TvExtensionPluginErrorText(
                stringResource(string.feat_setting_extension_not_installed)
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (actions.settings) {
                TvActionButton(
                    text = settingsActionLabel,
                    icon = Icons.Rounded.Extension,
                    semanticsLabel = actionDescription(settingsActionLabel),
                    focusRequester = focusRequesterFor(TvExtensionPluginAction.SETTINGS),
                    onClick = onOpenSettings,
                )
            }
            if (actions.disable) {
                TvActionButton(
                    text = disableActionLabel,
                    icon = Icons.Rounded.Block,
                    semanticsLabel = actionDescription(disableActionLabel),
                    focusRequester = focusRequesterFor(TvExtensionPluginAction.DISABLE),
                    onClick = onDisable,
                )
            }
            if (actions.enable) {
                TvActionButton(
                    text = enableActionLabel,
                    icon = Icons.Rounded.CheckCircle,
                    semanticsLabel = actionDescription(enableActionLabel),
                    focusRequester = focusRequesterFor(TvExtensionPluginAction.ENABLE),
                    onClick = onEnable,
                )
            }
            if (actions.revoke) {
                TvActionButton(
                    text = revokeActionLabel,
                    icon = Icons.Rounded.Block,
                    semanticsLabel = actionDescription(revokeActionLabel),
                    focusRequester = focusRequesterFor(TvExtensionPluginAction.REVOKE),
                    onClick = onRevoke,
                )
            }
            if (actions.reauthorize) {
                TvActionButton(
                    text = reauthorizeActionLabel,
                    icon = Icons.Rounded.CheckCircle,
                    semanticsLabel = actionDescription(reauthorizeActionLabel),
                    focusRequester = focusRequesterFor(TvExtensionPluginAction.REAUTHORIZE),
                    onClick = onReauthorize,
                )
            }
            if (actions.exportDiagnostics) {
                TvActionButton(
                    text = exportDiagnosticsActionLabel,
                    icon = Icons.Rounded.Refresh,
                    semanticsLabel = actionDescription(exportDiagnosticsActionLabel),
                    focusRequester = focusRequesterFor(
                        TvExtensionPluginAction.EXPORT_DIAGNOSTICS
                    ),
                    onClick = onExportDiagnostics,
                )
            }
            if (actions.clearData) {
                TvActionButton(
                    text = clearDataActionLabel,
                    icon = Icons.Rounded.Block,
                    semanticsLabel = actionDescription(clearDataActionLabel),
                    focusRequester = focusRequesterFor(TvExtensionPluginAction.CLEAR_DATA),
                    onClick = onClearData,
                )
            }
        }
    }
}

@Composable
private fun TvExtensionPluginErrorText(message: String) {
    Text(
        text = message,
        color = TvColors.Danger,
        fontSize = 14.sp,
        modifier = Modifier.semantics {
            error(message)
            liveRegion = LiveRegionMode.Polite
        },
    )
}

@Composable
private fun ExtensionClearConfirmation(
    plugin: InstalledPlugin,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    ExtensionDataRemovalConfirmation(
        plugin = plugin,
        title = stringResource(string.feat_setting_extension_clear_data_title),
        body = stringResource(string.feat_setting_extension_clear_data_body),
        confirmLabel = stringResource(string.feat_setting_extension_clear_data),
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}

@Composable
private fun ExtensionForgetConfirmation(
    plugin: InstalledPlugin,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    ExtensionDataRemovalConfirmation(
        plugin = plugin,
        title = stringResource(string.feat_setting_extension_forget_title),
        body = stringResource(string.feat_setting_extension_forget_body),
        confirmLabel = stringResource(string.feat_setting_extension_revoke),
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}

@Composable
private fun ExtensionDataRemovalConfirmation(
    plugin: InstalledPlugin,
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val bidiFormatter = rememberTvBidiFormatter()
    BackHandler(onBack = onCancel)
    val cancelFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val focusScrollClearancePx = with(LocalDensity.current) { 28.dp.roundToPx() }
    val pluginNameSegments = remember(
        plugin.displayName,
        plugin.packageName,
        bidiFormatter,
    ) {
        buildList {
            plugin.displayName?.let { displayName ->
                addAll(displayName.tvReadableSegments(bidiFormatter::natural))
            }
            addAll(plugin.packageName.tvReadableSegments(bidiFormatter::ltr))
        }
    }
    val bodySegments = remember(body) {
        body.tvReadableSegments()
    }
    val actionItemIndex = 1 + pluginNameSegments.size + bodySegments.size
    LaunchedEffect(
        plugin.packageName,
        plugin.serviceName,
        title,
        actionItemIndex,
        focusScrollClearancePx,
    ) {
        listState.scrollToItem(
            actionItemIndex,
            scrollOffset = -focusScrollClearancePx,
        )
        repeat(2) { withFrameNanos { } }
        cancelFocusRequester.requestFocus()
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .focusGroup(),
        contentPadding = PaddingValues(
            start = 48.dp,
            top = 48.dp,
            end = 64.dp,
            bottom = 72.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            TvReadableConfirmationText(
                text = title,
                color = TvColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        items(pluginNameSegments) { pluginNameSegment ->
            TvReadableConfirmationText(
                text = pluginNameSegment,
                color = TvColors.TextPrimary,
                fontSize = 20.sp,
            )
        }
        items(bodySegments) { bodySegment ->
            TvReadableConfirmationText(
                text = bodySegment,
                color = TvColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TvActionButton(
                    text = stringResource(android.R.string.cancel),
                    icon = Icons.Rounded.CheckCircle,
                    focusRequester = cancelFocusRequester,
                    onClick = onCancel,
                )
                TvActionButton(
                    text = confirmLabel,
                    icon = Icons.Rounded.Block,
                    onClick = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun TvReadableConfirmationText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (focused) Color.White.copy(alpha = 0.10f) else Color.Transparent,
                shape = shape,
            )
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                shape = shape,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {}
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
    )
}

@Composable
private fun TvReadableConfirmationBlock(
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (focused) Color.White.copy(alpha = 0.10f) else Color.Transparent,
                shape = shape,
            )
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                shape = shape,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {}
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
    }
}

@Composable
private fun ExtensionSettingsPanel(
    configuration: ExtensionSettingsConfiguration,
    operationError: String?,
    onClose: () -> Unit,
    onUpdate: (
        sectionId: String,
        fieldKey: String,
        editToken: ExtensionSettingEditToken,
        rawValue: String?,
    ) -> Unit,
) {
    val bidiFormatter = rememberTvBidiFormatter()
    val initialFocusRequester = remember { FocusRequester() }
    val firstFieldKey = configuration.sections.firstNotNullOfOrNull { section ->
        section.schema.fields.firstOrNull()?.let { field ->
            ExtensionSettingKeys.qualified(section.id, field.key)
        }
    }
    val draftValues = remember(configuration.extensionId) {
        mutableStateMapOf<String, String>().apply {
            configuration.sections.forEach { section ->
                section.schema.fields.forEach { field ->
                    val key = ExtensionSettingKeys.qualified(section.id, field.key)
                    if (field.type != ExtensionSettingType.SECRET) {
                        put(key, configuration.snapshot.values[key].tvPrimitiveContent())
                    }
                }
            }
        }
    }
    val validationRequested = remember(configuration.extensionId) {
        mutableStateMapOf<String, Boolean>()
    }
    val dirtyKeys = remember(configuration.extensionId) {
        mutableStateMapOf<String, Boolean>()
    }
    LaunchedEffect(configuration) {
        val activeKeys = mutableSetOf<String>()
        configuration.sections.forEach { section ->
            section.schema.fields.forEach { field ->
                val key = ExtensionSettingKeys.qualified(section.id, field.key)
                activeKeys += key
                if (dirtyKeys[key] != true) {
                    draftValues[key] = if (field.type == ExtensionSettingType.SECRET) {
                        ""
                    } else {
                        configuration.snapshot.values[key].tvPrimitiveContent()
                    }
                }
            }
        }
        draftValues.keys.retainAll(activeKeys)
        validationRequested.keys.retainAll(activeKeys)
        dirtyKeys.keys.retainAll(activeKeys)
    }
    LaunchedEffect(configuration.extensionId) {
        repeat(2) { withFrameNanos { } }
        initialFocusRequester.requestFocus()
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .focusGroup(),
        contentPadding = PaddingValues(
            start = 48.dp,
            top = 48.dp,
            end = 64.dp,
            bottom = 48.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "extension-settings:header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = stringResource(string.feat_setting_extension_settings),
                    color = TvColors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp),
                )
                TvActionButton(
                    text = stringResource(android.R.string.cancel),
                    icon = Icons.Rounded.Block,
                    focusRequester = initialFocusRequester.takeIf { firstFieldKey == null },
                    onClick = onClose,
                )
            }
        }
        if (configuration.sections.isEmpty()) {
            item(key = "extension-settings:empty") {
                Text(
                    stringResource(string.feat_setting_extension_settings_empty),
                    color = TvColors.TextSecondary,
                )
            }
        }
        operationError?.let { message ->
            item(key = "extension-settings:error") {
                Text(
                    text = message,
                    color = TvColors.Danger,
                    fontSize = 16.sp,
                    modifier = Modifier.semantics {
                        error(message)
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }
        }
        configuration.sections.forEach { section ->
            item(key = "extension-settings:section:${section.id}") {
                Text(
                    text = bidiFormatter.natural(section.title),
                    color = TvColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(
                items = section.schema.fields,
                key = { field ->
                    "extension-settings:field:" +
                        ExtensionSettingKeys.qualified(section.id, field.key)
                },
            ) { field ->
                val key = ExtensionSettingKeys.qualified(section.id, field.key)
                val editToken = checkNotNull(
                    configuration.editToken(section.id, field.key)
                )
                val inputError = field.extensionSettingInputError(
                    rawValue = draftValues[key].orEmpty(),
                    secretConfigured = key in configuration.snapshot.credentialHandles,
                )
                val accessibilityError = inputError
                    .takeIf {
                        validationRequested[key] == true ||
                            field.type == ExtensionSettingType.SINGLE_CHOICE
                    }
                    ?.let { error -> extensionSettingFieldErrorMessage(error) }
                TvExtensionSettingControl(
                    field = field,
                    rawValue = draftValues[key].orEmpty(),
                    secretConfigured = key in configuration.snapshot.credentialHandles,
                    focusRequester = initialFocusRequester.takeIf { key == firstFieldKey },
                    accessibilityError = accessibilityError,
                    onDraftChange = { value ->
                        draftValues[key] = value
                        dirtyKeys[key] = true
                    },
                    onUpdate = update@{ value ->
                        if (
                            value != null &&
                            field.extensionSettingInputError(
                                rawValue = value,
                                secretConfigured =
                                    key in configuration.snapshot.credentialHandles,
                            ) != null
                        ) {
                            validationRequested[key] = true
                            return@update
                        }
                        validationRequested[key] = false
                        dirtyKeys.remove(key)
                        draftValues[key] = if (field.type == ExtensionSettingType.SECRET) {
                            ""
                        } else {
                            value.orEmpty()
                        }
                        onUpdate(
                            section.id,
                            field.key,
                            editToken,
                            value
                                ?.takeUnless { it.isEmpty() && !field.required }
                                ?.let(field::normalizedExtensionSettingValue),
                        )
                    },
                )
                accessibilityError?.let { message ->
                    ProviderFieldError(message)
                }
            }
        }
    }
}

@Composable
private fun TvExtensionSettingControl(
    field: ExtensionSettingField,
    rawValue: String,
    secretConfigured: Boolean,
    focusRequester: FocusRequester?,
    accessibilityError: String?,
    onDraftChange: (String) -> Unit,
    onUpdate: (String?) -> Unit,
) {
    val requiredDescription =
        stringResource(string.feat_setting_provider_error_required)
    val saveActionLabel = stringResource(string.feat_setting_extension_setting_save)
    val clearActionLabel = stringResource(string.feat_setting_extension_setting_clear)
    val bidiFormatter = rememberTvBidiFormatter()
    val focusManager = LocalFocusManager.current
    val semanticFieldLabel = field.label.withoutBidiControls()
    val context = LocalContext.current
    val semanticFieldDescription = if (field.required) {
        context.getString(
            string.feat_setting_extension_field_required_description,
            semanticFieldLabel,
            requiredDescription,
        )
    } else {
        semanticFieldLabel
    }
    fun semanticChoiceDescription(choiceLabel: String): String = if (field.required) {
        context.getString(
            string.feat_setting_extension_choice_field_required_description,
            choiceLabel.withoutBidiControls(),
            semanticFieldLabel,
            requiredDescription,
        )
    } else {
        context.getString(
            string.feat_setting_extension_choice_field_description,
            choiceLabel.withoutBidiControls(),
            semanticFieldLabel,
        )
    }
    fun semanticActionDescription(actionLabel: String): String = context.getString(
        string.feat_setting_extension_action_field_description,
        actionLabel,
        semanticFieldLabel,
    )
    val displayLabel = bidiFormatter.natural(
        if (field.required) "${field.label} *" else field.label
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = displayLabel,
            color = TvColors.TextPrimary,
            fontSize = 16.sp,
        )
        field.description?.let { description ->
            Text(
                bidiFormatter.natural(description),
                color = TvColors.TextSecondary,
                fontSize = 14.sp,
            )
        }
        if (field.networkOrigin && accessibilityError == null) {
            Text(
                text = stringResource(string.feat_setting_extension_network_origin_save_notice),
                color = TvColors.Danger,
                fontSize = 14.sp,
            )
        }
        when (field.type) {
            ExtensionSettingType.BOOLEAN -> {
                TvActionButton(
                    text = stringResource(
                        if (rawValue.toBooleanStrictOrNull() == true) {
                            string.feat_setting_extension_state_enabled
                        } else {
                            string.feat_setting_extension_state_disabled
                        }
                    ),
                    icon = Icons.Rounded.CheckCircle,
                    focusRequester = focusRequester,
                    checked = rawValue.toBooleanStrictOrNull() == true,
                    semanticRole = Role.Switch,
                    semanticsLabel = semanticFieldDescription,
                    semanticsError = accessibilityError,
                    onClick = {
                        onUpdate((rawValue.toBooleanStrictOrNull() != true).toString())
                    },
                )
            }

            ExtensionSettingType.SINGLE_CHOICE -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.selectableGroup(),
                ) {
                    field.choices.forEach { choice ->
                        TvActionButton(
                            text = bidiFormatter.natural(choice.label),
                            icon = if (rawValue == choice.value) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.Extension
                            },
                            focusRequester = focusRequester.takeIf {
                                choice == field.choices.firstOrNull()
                            },
                            selected = rawValue == choice.value,
                            semanticRole = Role.RadioButton,
                            semanticsLabel = semanticChoiceDescription(choice.label),
                            semanticsError = accessibilityError,
                            onClick = { onUpdate(choice.value) },
                        )
                    }
                }
            }

            ExtensionSettingType.TEXT,
            ExtensionSettingType.NUMBER,
            ExtensionSettingType.SECRET -> {
                var focused by remember { mutableStateOf(false) }
                val actionFocusRequester = remember(field.key) { FocusRequester() }
                val singleLineInput =
                    field.type != ExtensionSettingType.TEXT || field.networkOrigin
                if (field.type == ExtensionSettingType.SECRET && secretConfigured) {
                    Text(
                        stringResource(string.feat_setting_extension_secret_configured),
                        color = TvColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) {
                                false
                            } else {
                                when (event.key) {
                                    Key.DirectionUp ->
                                        focusManager.moveFocus(FocusDirection.Up)

                                    Key.DirectionDown ->
                                        actionFocusRequester.requestFocus()

                                    else -> false
                                }
                            }
                        },
                ) {
                    BasicTextField(
                        value = rawValue,
                        onValueChange = onDraftChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .then(
                                focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
                            )
                            .onFocusChanged { focused = it.isFocused }
                            .semantics {
                                contentDescription = semanticFieldDescription
                                if (field.type == ExtensionSettingType.SECRET) {
                                    password()
                                }
                                accessibilityError?.let { message ->
                                    error(message)
                                }
                            }
                            .border(
                                width = if (focused) 3.dp else 1.dp,
                                color = if (focused) TvColors.Focus else TvColors.TextMuted,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        textStyle = TextStyle(
                            color = TvColors.TextPrimary,
                            fontSize = 16.sp,
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = when (field.type) {
                                ExtensionSettingType.NUMBER -> KeyboardType.Decimal
                                ExtensionSettingType.SECRET -> KeyboardType.Password
                                else -> KeyboardType.Text
                            },
                            autoCorrectEnabled = false,
                        ),
                        visualTransformation = if (field.type == ExtensionSettingType.SECRET) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        singleLine = singleLineInput,
                        minLines = 1,
                        maxLines = if (singleLineInput) 1 else 4,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TvActionButton(
                        text = saveActionLabel,
                        icon = Icons.Rounded.CheckCircle,
                        focusRequester = actionFocusRequester,
                        semanticsLabel = semanticActionDescription(saveActionLabel),
                        onClick = { onUpdate(rawValue) },
                    )
                    if (rawValue.isNotEmpty() || secretConfigured) {
                        TvActionButton(
                            text = clearActionLabel,
                            icon = Icons.Rounded.Block,
                            semanticsLabel = semanticActionDescription(clearActionLabel),
                            onClick = { onUpdate(null) },
                        )
                    }
                }
            }
        }
    }
}

private fun Any?.tvPrimitiveContent(): String = when (this) {
    is JsonPrimitive -> booleanOrNull?.toString() ?: contentOrNull.orEmpty()
    else -> ""
}

private fun tvExtensionCapabilityNameResource(capabilityId: String): Int? = when (capabilityId) {
    ExtensionCapabilityIds.Network.id ->
        string.feat_setting_extension_capability_name_network
    ExtensionCapabilityIds.CredentialRead.id ->
        string.feat_setting_extension_capability_name_credential_read
    ExtensionCapabilityIds.CredentialWrite.id ->
        string.feat_setting_extension_capability_name_credential_write
    ExtensionCapabilityIds.SubscriptionRead.id ->
        string.feat_setting_extension_capability_name_subscription_read
    ExtensionCapabilityIds.SubscriptionWrite.id ->
        string.feat_setting_extension_capability_name_subscription_write
    ExtensionCapabilityIds.PlaybackResolve.id ->
        string.feat_setting_extension_capability_name_playback_resolve
    ExtensionCapabilityIds.EpgRead.id ->
        string.feat_setting_extension_capability_name_epg_read
    ExtensionCapabilityIds.MetadataWrite.id ->
        string.feat_setting_extension_capability_name_metadata_write
    ExtensionCapabilityIds.SettingsContribute.id ->
        string.feat_setting_extension_capability_name_settings_contribute
    ExtensionCapabilityIds.SearchRead.id ->
        string.feat_setting_extension_capability_name_search_read
    ExtensionCapabilityIds.BackgroundTask.id ->
        string.feat_setting_extension_capability_name_background_task
    else -> null
}

@Composable
private fun ContentRow(
    channels: List<Channel>,
    onPlay: (Channel) -> Unit,
    onFocused: (Channel) -> Unit = {},
    firstItemFocusRequester: FocusRequester? = null
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(start = 48.dp, top = 8.dp, end = 48.dp, bottom = 8.dp),
        modifier = Modifier.focusGroup()
    ) {
        itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
            ChannelCard(
                channel = channel,
                onPlay = { onPlay(channel) },
                onFocused = { onFocused(channel) },
                focusRequester = firstItemFocusRequester.takeIf { index == 0 },
                compact = true,
                modifier = Modifier
                    .widthIn(min = 104.dp, max = 120.dp)
                    .aspectRatio(2f / 3f)
            )
        }
    }
}

@Composable
private fun ChannelGrid(
    channels: List<Channel>,
    onPlay: (Channel) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    firstItemFocusRequester: FocusRequester? = null
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(168.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = modifier.focusGroup()
    ) {
        itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
            ChannelCard(
                channel = channel,
                onPlay = { onPlay(channel) },
                focusRequester = firstItemFocusRequester.takeIf { index == 0 }
            )
        }
    }
}

@Composable
private fun EmptyLibraryScreen() {
    val largeTextLayout = tvLargeTextLayout(LocalDensity.current.fontScale)
    LazyColumn(
        verticalArrangement = Arrangement.Center,
        contentPadding = PaddingValues(start = 48.dp, top = 48.dp, end = 64.dp, bottom = 48.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            if (largeTextLayout.stackEmptyLibrary) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    EmptyLibraryDescription(expandedWidth = true)
                    SetupPanel(
                        minHeight = largeTextLayout.emptySetupMinHeightDp.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 640.dp),
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    EmptyLibraryDescription(
                        expandedWidth = false,
                        modifier = Modifier.weight(1f),
                    )
                    SetupPanel(
                        modifier = Modifier
                            .weight(0.88f)
                            .widthIn(max = 420.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryDescription(
    expandedWidth: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(string.tv_home_title),
            color = TvColors.TextPrimary,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = TvFonts.Body,
        )
        Text(
            text = stringResource(string.tv_empty_library_title),
            color = TvColors.TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = TvFonts.Body,
        )
        Text(
            text = stringResource(string.tv_empty_library_subtitle),
            color = TvColors.TextSecondary,
            fontSize = 17.sp,
            lineHeight = 25.sp,
            fontFamily = TvFonts.Body,
            modifier = Modifier.fillMaxWidth(if (expandedWidth) 1f else 0.82f),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth(if (expandedWidth) 1f else 0.72f)
                .widthIn(max = if (expandedWidth) 720.dp else 420.dp),
        ) {
            InfoPill(
                text = stringResource(string.tv_empty_library_phone_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            InfoPill(
                text = stringResource(string.tv_empty_library_restore_hint),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SetupPanel(
    modifier: Modifier = Modifier,
    minHeight: Dp? = null,
) {
    val sizeModifier = if (minHeight == null) {
        Modifier.aspectRatio(1.18f)
    } else {
        Modifier.heightIn(min = minHeight)
    }
    FocusFrame(
        onClick = {},
        enabled = false,
        focusableWhenDisabled = minHeight != null,
        semanticRole = null,
        modifier = Modifier
            .then(modifier)
            .then(sizeModifier),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        0f to TvColors.SurfaceRaised,
                        1f to TvColors.BackgroundSoft
                    )
                )
                .padding(24.dp)
        ) {
            SectionTitle(
                title = stringResource(string.tv_empty_library_panel_title),
                subtitle = stringResource(string.tv_empty_library_panel_subtitle)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SetupStep(text = stringResource(string.tv_empty_library_step_sources))
                SetupStep(text = stringResource(string.tv_empty_library_step_sync))
                SetupStep(text = stringResource(string.tv_empty_library_step_watch))
            }
        }
    }
}

@Composable
private fun SetupStep(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(TvColors.Focus)
        )
        Text(
            text = text,
            color = TvColors.TextSecondary,
            fontSize = 14.sp,
            fontFamily = TvFonts.Body,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
