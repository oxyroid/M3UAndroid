package com.m3u.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Tv
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.isSeries
import com.m3u.data.database.model.isVod
import com.m3u.i18n.R.string

@Composable
fun TvBackdrop(channel: Channel?) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = channel?.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to TvColors.Background,
                        0.58f to TvColors.Background.copy(alpha = 0.92f),
                        1f to TvColors.Background.copy(alpha = 0.72f)
                    )
                )
        )
    }
}

@Composable
fun TvNavigationRail(
    selected: TvDestination,
    onSelect: (TvDestination) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(112.dp)
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
    ) {
        TvDestination.entries.forEach { destination ->
            RailItem(
                destination = destination,
                selected = destination == selected,
                onClick = { onSelect(destination) }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RailItem(
    destination: TvDestination,
    selected: Boolean,
    onClick: () -> Unit
) {
    FocusFrame(
        onClick = onClick,
        selected = selected,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.size(64.dp)
    ) { focused ->
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.label(),
            tint = if (selected || focused) TvColors.OnFocus else TvColors.TextSecondary,
            modifier = Modifier
                .align(Alignment.Center)
                .size(28.dp)
        )
    }
}

@Composable
private fun TvDestination.label(): String = when (this) {
    TvDestination.Home -> stringResource(string.tv_home_title)
    TvDestination.Library -> stringResource(string.tv_library_title)
    TvDestination.Favorites -> stringResource(string.tv_favorites_title)
    TvDestination.Status -> stringResource(string.tv_settings_title)
}

@Composable
fun FocusFrame(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    focusRequester: FocusRequester? = null,
    focusedScale: Float = 1.08f,
    focusedBorderWidth: Dp = 4.dp,
    focusedBorderColor: Color = Color.White,
    onFocus: () -> Unit = {},
    onKey: (KeyEvent) -> Boolean = { false },
    content: @Composable BoxScope.(focused: Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled) focusedScale else 1f,
        label = "tv-focus-scale"
    )
    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (focused && enabled) 18.dp else 0.dp,
                shape = shape,
                clip = false
            )
            .clip(shape)
            .background(
                when {
                    focused && enabled -> TvColors.Focus
                    selected -> TvColors.Focus.copy(alpha = 0.72f)
                    else -> TvColors.Surface.copy(alpha = 0.86f)
                }
            )
            .border(
                BorderStroke(
                    width = if (focused) focusedBorderWidth else 1.dp,
                    color = when {
                        focused -> focusedBorderColor
                        selected -> TvColors.Focus
                        else -> Color.White.copy(alpha = 0.08f)
                    }
                ),
                shape = shape
            )
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .then(
                if (enabled) {
                    Modifier
                        .onKeyEvent { event ->
                            when {
                                onKey(event) -> true
                                event.type == KeyEventType.KeyUp && event.key.isDpadConfirmKey() -> {
                                    onClick()
                                    true
                                }
                                else -> false
                            }
                        }
                        .clickable(onClick = onClick)
                        .focusable()
                } else {
                    Modifier
                }
            )
    ) {
        content(focused)
    }
}

@Composable
fun TvIconActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    FocusFrame(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        focusRequester = focusRequester,
        modifier = modifier.size(56.dp)
    ) { focused ->
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (focused) TvColors.OnFocus else TvColors.TextPrimary,
            modifier = Modifier
                .align(Alignment.Center)
                .size(28.dp)
        )
    }
}

@Composable
fun TvActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    showTextWhenUnfocused: Boolean = true
) {
    FocusFrame(
        onClick = onClick,
        selected = false,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        focusRequester = focusRequester,
        modifier = modifier.height(48.dp)
    ) { focused ->
        val showText = focused || showTextWhenUnfocused
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = if (showText) 16.dp else 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    !enabled -> TvColors.TextMuted
                    focused -> TvColors.OnFocus
                    else -> TvColors.TextPrimary
                },
                modifier = Modifier.size(24.dp)
            )
            if (showText) {
                Text(
                    text = text,
                    color = when {
                        !enabled -> TvColors.TextMuted
                        focused -> TvColors.OnFocus
                        else -> TvColors.TextPrimary
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = TvFonts.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SectionTitle(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Text(
            text = title,
            color = TvColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = TvFonts.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = TvColors.TextSecondary,
            fontSize = 14.sp,
            fontFamily = TvFonts.Body,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ChannelCard(
    channel: Channel,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    compact: Boolean = false
) {
    FocusFrame(
        onClick = onPlay,
        modifier = modifier,
        focusRequester = focusRequester,
        onFocus = onFocused,
        shape = RoundedCornerShape(12.dp)
    ) { focused ->
        val primaryTextColor = if (focused) TvColors.OnFocus else TvColors.TextPrimary
        val secondaryTextColor = if (focused) TvColors.OnFocus.copy(alpha = 0.78f) else TvColors.TextSecondary
        if (compact) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    PosterArt(
                        model = channel.cover,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                ) {
                    Text(
                        text = channel.title.title(),
                        color = primaryTextColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = TvFonts.Body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Box {
                    PosterArt(
                        model = channel.cover,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                Text(
                    text = channel.title.title(),
                    color = primaryTextColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = TvFonts.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = channel.category.ifBlank { stringResource(string.feat_playlist_scheme_unknown) },
                    color = secondaryTextColor,
                    fontSize = 12.sp,
                    fontFamily = TvFonts.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PlaylistCard(
    playlist: Playlist,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    FocusFrame(
        onClick = onClick,
        selected = selected,
        modifier = modifier,
        focusRequester = focusRequester,
        shape = RoundedCornerShape(12.dp)
    ) { focused ->
        val active = selected || focused
        val primaryTextColor = if (active) TvColors.OnFocus else TvColors.TextPrimary
        val secondaryTextColor = if (active) TvColors.OnFocus.copy(alpha = 0.72f) else TvColors.TextSecondary
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) TvColors.OnFocus else TvColors.Focus)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    contentDescription = null,
                    tint = if (active) TvColors.Focus else TvColors.OnFocus,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = playlist.title.title(),
                    color = primaryTextColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = TvFonts.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = playlistLabel(playlist, count),
                    color = secondaryTextColor,
                    fontSize = 12.sp,
                    fontFamily = TvFonts.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MetricTile(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    FocusFrame(
        onClick = {},
        enabled = false,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TvColors.Focus,
                modifier = Modifier.size(32.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = value,
                    color = TvColors.TextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TvFonts.Accent,
                    maxLines = 1
                )
                Text(
                    text = title,
                    color = TvColors.TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = TvFonts.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PosterArt(
    model: String?,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.background(TvColors.SurfaceRaised)
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (model.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = TvColors.TextMuted,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

@Composable
fun InfoPill(
    text: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 40.dp
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .height(minHeight)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = text,
            color = TvColors.TextSecondary,
            fontSize = 13.sp,
            fontFamily = TvFonts.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun Key.isDpadConfirmKey(): Boolean = this == Key.DirectionCenter ||
    this == Key.Enter ||
    this == Key.NumPadEnter

@Composable
fun playlistLabel(playlist: Playlist, count: Int): String {
    val type = when {
        playlist.isSeries -> stringResource(string.tv_playlist_type_series)
        playlist.isVod -> stringResource(string.tv_playlist_type_vod)
        else -> playlist.source.value.uppercase()
    }
    return stringResource(string.tv_playlist_label, type, count)
}