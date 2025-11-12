package com.m3u.smartphone.ui.business.foryou.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.m3u.data.database.model.Channel
import com.m3u.smartphone.ui.material.model.LocalSpacing
import com.m3u.smartphone.ui.material.model.NetflixTheme
import com.m3u.smartphone.ui.material.model.glassmorphismHaze
import com.m3u.smartphone.ui.material.model.scrim

/**
 * Netflix-style Continue Watching hero banner
 * Large prominent card at the top of the home screen showing the last watched content
 */
@Composable
fun ContinueWatchingHero(
    channel: Channel,
    position: Long,
    duration: Long = 0L,
    onClick: () -> Unit,
    onInfoClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        label = "scale"
    )

    val elevation by animateDpAsState(
        targetValue = if (isPressed) NetflixTheme.Elevation.Low else NetflixTheme.Elevation.Medium,
        label = "elevation"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(450.dp)
            .scale(scale)
            .clickable(
                onClick = onClick,
                onClickLabel = "Continue watching ${channel.title}"
            )
    ) {
        // Background image with gradient overlay
        AsyncImage(
            model = channel.cover,
            contentDescription = channel.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
        )

        // Gradient scrim for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scrim(NetflixTheme.HeroScrim)
        )

        // Content overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.medium),
            verticalArrangement = Arrangement.Bottom
        ) {
            // "Continue Watching" label with glassmorphism
            Text(
                text = "Continue Watching",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .glassmorphismHaze(
                        backgroundColor = NetflixTheme.GlassLight.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            Spacer(modifier = Modifier.height(spacing.small))

            // Title
            Text(
                text = channel.title,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    lineHeight = 40.sp
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.85f)
            )

            Spacer(modifier = Modifier.height(spacing.small))

            // Category/Description if available
            if (channel.category.isNotEmpty()) {
                Text(
                    text = channel.category,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(spacing.medium))
            }

            // Progress indicator
            if (duration > 0 && position > 0) {
                val progress = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                Column {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = NetflixTheme.NetflixRed,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(spacing.extraSmall))
                    Text(
                        text = "${(progress * 100).toInt()}% watched",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.height(spacing.medium))
            }

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Info button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .glassmorphismHaze(
                            backgroundColor = NetflixTheme.GlassLight.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                        .clickable(onClick = onInfoClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "More info",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Compact version of Continue Watching for smaller spaces
 */
@Composable
fun ContinueWatchingCard(
    channel: Channel,
    position: Long,
    duration: Long = 0L,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    Box(
        modifier = modifier
            .width(300.dp)
            .height(170.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        // Thumbnail
        AsyncImage(
            model = channel.cover,
            contentDescription = channel.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scrim(NetflixTheme.BottomScrim)
        )

        // Progress indicator
        if (duration > 0 && position > 0) {
            val progress = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter),
                color = NetflixTheme.NetflixRed,
                trackColor = Color.Transparent
            )
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.medium),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = channel.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Play icon overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.small),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Play",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
