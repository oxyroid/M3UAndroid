package com.m3u.tv.screens.foryou

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.LinearProgressIndicator
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ShapeDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.m3u.data.database.model.Channel
import com.m3u.tv.ui.theme.NetflixTvTheme

/**
 * Netflix-style Continue Watching hero for Android TV
 * Large prominent banner showing last watched content with progress
 * Optimized for remote control navigation
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContinueWatchingHero(
    channel: Channel,
    position: Long,
    duration: Long = 0L,
    onPlay: () -> Unit,
    onInfo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(500.dp)
            .padding(NetflixTvTheme.Spacing.medium)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isFocused) NetflixTvTheme.FocusBorderWidth else 0.dp,
                color = if (isFocused) NetflixTvTheme.FocusedBorder else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        // Background image with gradient overlay
        AsyncImage(
            model = channel.cover,
            contentDescription = channel.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawRect(NetflixTvTheme.HeroScrim)
                }
        )

        // Content overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(NetflixTvTheme.Spacing.large),
            verticalArrangement = Arrangement.Bottom
        ) {
            // "Continue Watching" badge
            Box(
                modifier = Modifier
                    .background(
                        color = NetflixTvTheme.GlassLight,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Continue Watching",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(NetflixTvTheme.Spacing.small))

            // Title
            Text(
                text = channel.title,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 56.sp,
                    lineHeight = 64.sp,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.8f),
                        offset = Offset(x = 2f, y = 4f),
                        blurRadius = 8f
                    )
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.8f)
            )

            // Category/Description
            if (channel.category.isNotEmpty()) {
                Spacer(modifier = Modifier.height(NetflixTvTheme.Spacing.small))
                Text(
                    text = channel.category,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 24.sp,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            offset = Offset(x = 2f, y = 4f),
                            blurRadius = 8f
                        )
                    ),
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(NetflixTvTheme.Spacing.medium))

            // Progress indicator
            if (duration > 0 && position > 0) {
                val progress = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

                Column {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(6.dp),
                        color = NetflixTvTheme.NetflixRed,
                        trackColor = Color.White.copy(alpha = 0.3f),
                        drawStopIndicator = {}
                    )

                    Spacer(modifier = Modifier.height(NetflixTvTheme.Spacing.extraSmall))

                    Text(
                        text = "${(progress * 100).toInt()}% watched",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 18.sp,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.8f),
                                offset = Offset(x = 2f, y = 4f),
                                blurRadius = 4f
                            )
                        ),
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(NetflixTvTheme.Spacing.medium))
            }

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(NetflixTvTheme.Spacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play button (primary)
                Button(
                    onClick = onPlay,
                    modifier = Modifier
                        .onFocusChanged { isFocused = it.hasFocus }
                        .height(64.dp),
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(shape = ShapeDefaults.ExtraSmall),
                    scale = ButtonDefaults.scale(
                        focusedScale = 1.1f
                    ),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Resume",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    )
                }

                // Info button (secondary)
                Button(
                    onClick = onInfo,
                    modifier = Modifier.height(64.dp),
                    colors = ButtonDefaults.colors(
                        containerColor = NetflixTvTheme.GlassLight,
                        contentColor = Color.White,
                        focusedContainerColor = NetflixTvTheme.GlassMedium,
                        focusedContentColor = Color.White
                    ),
                    shape = ButtonDefaults.shape(shape = ShapeDefaults.ExtraSmall),
                    scale = ButtonDefaults.scale(
                        focusedScale = 1.1f
                    ),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "More info",
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Info",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    )
                }
            }
        }
    }
}
