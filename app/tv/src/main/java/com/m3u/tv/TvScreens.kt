package com.m3u.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.business.setting.ProviderDiscoveryState
import com.m3u.business.setting.ProviderSettingFieldError
import com.m3u.business.setting.ProviderSubscriptionForm
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
    val largeTextLayout = tvLargeTextLayout(LocalDensity.current.fontScale)
    var pendingTrust by remember { mutableStateOf<InstalledPlugin?>(null) }
    var pendingReauthorization by remember { mutableStateOf(false) }
    var pendingRevoke by remember { mutableStateOf<InstalledPlugin?>(null) }
    var pendingClear by remember { mutableStateOf<InstalledPlugin?>(null) }
    val trustCancellationFocusRequester = remember { FocusRequester() }

    LaunchedEffect(pendingTrust) {
        if (pendingTrust != null) {
            yield()
            trustCancellationFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(state.externalExtensionsEnabled) {
        if (!state.externalExtensionsEnabled) {
            pendingTrust = null
            pendingReauthorization = false
            pendingRevoke = null
            pendingClear = null
            onCloseExtensionSettings()
        }
    }

    pendingRevoke?.let { plugin ->
        ExtensionForgetConfirmation(
            plugin = plugin,
            onConfirm = {
                pendingRevoke = null
                onRevokeExtension(
                    plugin.packageName,
                    plugin.serviceName,
                    plugin.extensionId,
                )
            },
            onCancel = { pendingRevoke = null },
        )
        return
    }

    pendingClear?.let { plugin ->
        ExtensionClearConfirmation(
            plugin = plugin,
            onConfirm = {
                pendingClear = null
                onClearExtensionData(
                    plugin.packageName,
                    plugin.serviceName,
                    plugin.extensionId,
                )
            },
            onCancel = { pendingClear = null },
        )
        return
    }

    state.extensionSettings?.let { configuration ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(start = 48.dp, top = 48.dp, end = 64.dp, bottom = 48.dp),
            modifier = Modifier.fillMaxSize().focusGroup(),
        ) {
            item {
                ExtensionSettingsPanel(
                    configuration = configuration,
                    onClose = onCloseExtensionSettings,
                    onUpdate = onUpdateExtensionSetting,
                )
            }
        }
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
            cancelFocusRequester = trustCancellationFocusRequester,
            onConfirm = {
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
                pendingTrust = null
                pendingReauthorization = false
            },
        )
        return
    }

    val extensionOperationFailedMessage =
        stringResource(string.feat_setting_extension_operation_failed)
    LazyColumn(
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
            items = state.providerAccounts.filter(ProviderAccountSummary::requiresReauthentication),
            key = { account -> "reauth:${account.playlistUrl}" },
        ) { account ->
            ProviderReauthenticationCard(
                account = account,
                onReauthenticate = { onReauthenticateProvider(account.playlistUrl) },
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
                            val providerLabel = if (selectableVariants.size == 1) {
                                bidiFormatter.natural(provider.descriptor.displayName)
                            } else {
                                stringResource(
                                    string.feat_setting_provider_source_with_provider,
                                    bidiFormatter.natural(provider.descriptor.displayName),
                                    bidiFormatter.natural(variant.displayName),
                                )
                            }
                            val providerId = bidiFormatter.ltr(
                                provider.descriptor.providerId.value
                            )
                            val isExternal =
                                provider.executionKind ==
                                    SubscriptionProviderExecutionKind.EXTERNAL
                            TvActionButton(
                                text = if (isExternal) {
                                    stringResource(
                                        string.feat_setting_provider_choice_with_identifier,
                                        providerLabel,
                                        providerId,
                                    )
                                } else {
                                    providerLabel
                                },
                                icon = Icons.Rounded.Extension,
                                semanticsLabel = if (isExternal) {
                                    stringResource(
                                        string.feat_setting_provider_choice_with_identifier_description,
                                        providerLabel,
                                        providerId,
                                    )
                                } else {
                                    null
                                },
                                onClick = {
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
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }
        }
        item {
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
                ExtensionPluginCard(
                    plugin = plugin,
                    onEnable = { pendingTrust = plugin },
                    onReauthorize = {
                        pendingReauthorization = true
                        pendingTrust = plugin
                    },
                    onDisable = { plugin.extensionId?.let(onDisableExtension) },
                    onRevoke = { pendingRevoke = plugin },
                    onOpenSettings = { plugin.extensionId?.let(onOpenExtensionSettings) },
                    onClearData = { pendingClear = plugin },
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
            onClick = onSubmit,
        )
    }
}

@Composable
private fun ProviderReauthenticationCard(
    account: ProviderAccountSummary,
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
            stringResource(string.feat_setting_provider_added, feedback.channelCount) to TvColors.Focus
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
        }
    )

@Composable
private fun ProviderFieldError(message: String) {
    Text(
        text = message,
        color = TvColors.Danger,
        fontSize = 14.sp,
        modifier = Modifier.clearAndSetSemantics {},
    )
}

@Composable
private fun ExtensionAuthorizationConfirmation(
    plugin: InstalledPlugin,
    reauthorization: Boolean,
    cancelFocusRequester: FocusRequester,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val bidiFormatter = rememberTvBidiFormatter()
    BackHandler(onBack = onCancel)
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(start = 48.dp, top = 48.dp, end = 64.dp, bottom = 48.dp),
        modifier = Modifier.fillMaxSize().focusGroup(),
    ) {
        item {
            Text(
                text = stringResource(string.feat_setting_extension_confirm_title),
                color = TvColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            Text(
                text = stringResource(
                    string.feat_setting_extension_confirm_identity,
                    bidiFormatter.ltr(plugin.packageName),
                    bidiFormatter.ltr(
                        plugin.certificateSha256.chunked(16).joinToString(" ")
                    ),
                    bidiFormatter.natural(plugin.displayName.orEmpty()),
                    bidiFormatter.natural(plugin.developer.orEmpty()),
                    bidiFormatter.ltr(plugin.version.orEmpty()),
                ),
                color = TvColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        plugin.previousCertificateSha256?.let { previousCertificate ->
            item {
                Text(
                    text = stringResource(
                        string.feat_setting_extension_certificate_repin,
                        bidiFormatter.ltr(previousCertificate.chunked(16).joinToString(" ")),
                        bidiFormatter.ltr(
                            plugin.certificateSha256.chunked(16).joinToString(" ")
                        ),
                    ),
                    color = TvColors.Danger,
                    fontSize = 14.sp,
                )
            }
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TvActionButton(
                    text = stringResource(android.R.string.cancel),
                    icon = Icons.Rounded.Block,
                    focusRequester = cancelFocusRequester,
                    onClick = onCancel,
                )
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
        item {
            Text(
                text = stringResource(string.feat_setting_extension_requested_capabilities),
                color = TvColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (plugin.capabilityPermissions.isEmpty()) {
            item { Text("—", color = TvColors.TextSecondary, fontSize = 16.sp) }
        } else {
            items(
                items = plugin.capabilityPermissions,
                key = { permission -> permission.id },
            ) { permission ->
                val requirement = stringResource(
                    if (permission.required) {
                        string.feat_setting_extension_capability_required
                    } else {
                        string.feat_setting_extension_capability_optional
                    }
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${bidiFormatter.ltr(permission.id)} ($requirement)",
                        color = TvColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        bidiFormatter.natural(permission.reason),
                        color = TvColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                }
            }
        }
        item {
            Text(
                text = stringResource(string.feat_setting_extension_network_origins),
                color = TvColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (plugin.networkOrigins.isEmpty()) {
            item { Text("—", color = TvColors.TextSecondary, fontSize = 16.sp) }
        } else {
            items(
                items = plugin.networkOrigins.sorted(),
                key = { origin -> origin },
            ) { origin ->
                Text(
                    bidiFormatter.ltr(origin),
                    color = TvColors.TextSecondary,
                    fontSize = 16.sp,
                )
            }
        }
        if (plugin.networkOriginSettingFields.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(
                        string.feat_setting_extension_network_origin_settings,
                        plugin.networkOriginSettingFields.sorted()
                            .joinToString(transform = bidiFormatter::ltr),
                    ),
                    color = TvColors.TextSecondary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun ExtensionPluginCard(
    plugin: InstalledPlugin,
    onEnable: () -> Unit,
    onReauthorize: () -> Unit,
    onDisable: () -> Unit,
    onRevoke: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearData: () -> Unit,
    onExportDiagnostics: () -> Unit,
) {
    val bidiFormatter = rememberTvBidiFormatter()
    val actions = extensionPluginActionAvailability(
        enabled = plugin.enabled,
        state = plugin.state,
        hasExtensionId = plugin.extensionId != null,
        installed = plugin.installed,
        signatureChanged = plugin.signatureChanged,
        hasInspectionError = plugin.inspectionError != null,
        hasAuthorizationToken = plugin.authorizationToken != null,
        trusted = plugin.trusted,
        canClearData = plugin.canClearData,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = plugin.displayName?.let(bidiFormatter::natural)
                ?: bidiFormatter.ltr(plugin.packageName),
            color = TvColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = listOfNotNull(
                plugin.developer?.let(bidiFormatter::natural),
                plugin.version?.let { bidiFormatter.ltr("v$it") },
            ).joinToString(" · "),
            color = TvColors.TextSecondary,
            fontSize = 14.sp,
        )
        Text(
            bidiFormatter.ltr(plugin.certificateSha256),
            color = TvColors.TextMuted,
            fontSize = 12.sp,
        )
        val unapprovedNetworkOrigins = plugin.networkOrigins - plugin.approvedNetworkOrigins
        if (plugin.trusted && unapprovedNetworkOrigins.isNotEmpty()) {
            Text(
                text = stringResource(
                    string.feat_setting_extension_network_reauthorization_required,
                    unapprovedNetworkOrigins.sorted()
                        .joinToString(transform = bidiFormatter::ltr),
                ),
                color = TvColors.Danger,
                fontSize = 14.sp,
            )
        }
        plugin.inspectionError?.let {
            Text(
                stringResource(string.feat_setting_extension_inspection_failed),
                color = TvColors.Danger,
                fontSize = 14.sp,
            )
        }
        if (!plugin.installed) {
            Text(
                stringResource(string.feat_setting_extension_not_installed),
                color = TvColors.Danger,
                fontSize = 14.sp,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (actions.settings) {
                TvActionButton(
                    text = stringResource(string.feat_setting_extension_settings),
                    icon = Icons.Rounded.Extension,
                    onClick = onOpenSettings,
                )
            }
            if (actions.disable) {
                TvActionButton(
                    text = stringResource(string.feat_setting_extension_disable),
                    icon = Icons.Rounded.Block,
                    onClick = onDisable,
                )
            }
            if (actions.enable) {
                TvActionButton(
                    text = stringResource(string.feat_setting_extension_enable),
                    icon = Icons.Rounded.CheckCircle,
                    onClick = onEnable,
                )
            }
            if (actions.revoke) {
                TvActionButton(
                    text = stringResource(string.feat_setting_extension_revoke),
                    icon = Icons.Rounded.Block,
                    onClick = onRevoke,
                )
            }
            if (actions.reauthorize) {
                TvActionButton(
                    text = stringResource(string.feat_setting_extension_reauthorize),
                    icon = Icons.Rounded.CheckCircle,
                    onClick = onReauthorize,
                )
            }
            if (actions.exportDiagnostics) {
                TvActionButton(
                    text = stringResource(string.feat_setting_extension_export_diagnostics),
                    icon = Icons.Rounded.Refresh,
                    onClick = onExportDiagnostics,
                )
            }
            if (actions.clearData) {
                TvActionButton(
                    text = stringResource(string.feat_setting_extension_clear_data),
                    icon = Icons.Rounded.Block,
                    onClick = onClearData,
                )
            }
        }
    }
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
    LaunchedEffect(plugin.packageName, plugin.serviceName) {
        repeat(2) { withFrameNanos { } }
        cancelFocusRequester.requestFocus()
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = title,
            color = TvColors.TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = plugin.displayName?.let(bidiFormatter::natural)
                ?: bidiFormatter.ltr(plugin.packageName),
            color = TvColors.TextPrimary,
            fontSize = 20.sp,
        )
        Text(
            text = body,
            color = TvColors.TextSecondary,
            fontSize = 16.sp,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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

@Composable
private fun ExtensionSettingsPanel(
    configuration: ExtensionSettingsConfiguration,
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
    val draftValues = remember(configuration) {
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
    LaunchedEffect(configuration.extensionId) {
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(string.feat_setting_extension_settings),
                color = TvColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            TvActionButton(
                text = stringResource(android.R.string.cancel),
                icon = Icons.Rounded.Block,
                focusRequester = initialFocusRequester.takeIf { firstFieldKey == null },
                onClick = onClose,
            )
        }
        if (configuration.sections.isEmpty()) {
            Text(
                stringResource(string.feat_setting_extension_settings_empty),
                color = TvColors.TextSecondary,
            )
        }
        configuration.sections.forEach { section ->
            Text(
                text = bidiFormatter.natural(section.title),
                color = TvColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            section.schema.fields.forEach { field ->
                val key = ExtensionSettingKeys.qualified(section.id, field.key)
                val editToken = checkNotNull(
                    configuration.editToken(section.id, field.key)
                )
                TvExtensionSettingControl(
                    field = field,
                    rawValue = draftValues[key].orEmpty(),
                    secretConfigured = key in configuration.snapshot.credentialHandles,
                    focusRequester = initialFocusRequester.takeIf { key == firstFieldKey },
                    accessibilityError = null,
                    onDraftChange = { value -> draftValues[key] = value },
                    onUpdate = { value ->
                        onUpdate(section.id, field.key, editToken, value)
                    },
                )
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
        stringResource(string.feat_setting_extension_capability_required)
    val bidiFormatter = rememberTvBidiFormatter()
    val focusManager = LocalFocusManager.current
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
        if (field.networkOrigin) {
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
                    semanticsLabel = bidiFormatter.natural(field.label),
                    modifier = Modifier.semantics {
                        if (field.required) {
                            stateDescription = requiredDescription
                        }
                        accessibilityError?.let { message ->
                            error(message)
                        }
                    },
                    onClick = {
                        onUpdate((rawValue.toBooleanStrictOrNull() != true).toString())
                    },
                )
            }

            ExtensionSettingType.SINGLE_CHOICE -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .selectableGroup()
                        .semantics {
                            if (field.required) {
                                stateDescription = requiredDescription
                            }
                            accessibilityError?.let { message ->
                                error(message)
                            }
                        },
                ) {
                    field.choices.forEach { choice ->
                        TvActionButton(
                            text = bidiFormatter.natural(choice.label),
                            icon = if (rawValue == choice.value) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.Extension
                            },
                            focusRequester = focusRequester.takeIf { choice == field.choices.firstOrNull() },
                            selected = rawValue == choice.value,
                            semanticRole = Role.RadioButton,
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
                                contentDescription = bidiFormatter.natural(field.label)
                                if (field.required) {
                                    stateDescription = requiredDescription
                                }
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
                        singleLine = true,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TvActionButton(
                        text = stringResource(string.feat_setting_extension_setting_save),
                        icon = Icons.Rounded.CheckCircle,
                        focusRequester = actionFocusRequester,
                        onClick = { onUpdate(rawValue) },
                    )
                    if (rawValue.isNotEmpty() || secretConfigured) {
                        TvActionButton(
                            text = stringResource(string.feat_setting_extension_setting_clear),
                            icon = Icons.Rounded.Block,
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
