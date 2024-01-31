package com.m3u.androidApp.ui.scaffold

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import com.m3u.core.wrapper.Message
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Destination
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.Fob
import kotlinx.collections.immutable.ImmutableList

@Composable
@InternalComposeApi
fun AppScaffoldTvImpl(
    rootDestination: Destination.Root?,
    rootDestinations: ImmutableList<Destination.Root>,
    fob: Fob?,
    title: String,
    message: Message,
    navigateToRoot: (Destination.Root) -> Unit,
    onBackPressed: (() -> Unit)?,
    actions: ImmutableList<Action>,
    content: @Composable (PaddingValues) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Row(modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(
                spacing.medium,
                Alignment.CenterVertically
            ),
            modifier = Modifier
                .fillMaxHeight()
                .padding(spacing.medium)
        ) {
            Items(rootDestinations) { currentRootDestination ->
                NavigationItemLayout(
                    rootDestination = rootDestination,
                    fob = fob,
                    currentRootDestination = currentRootDestination,
                    navigateToRoot = navigateToRoot
                ) { selected: Boolean,
                    onClick: () -> Unit,
                    icon: @Composable () -> Unit,
                    _: @Composable () -> Unit ->
                    val source = remember { MutableInteractionSource() }
                    val focused by source.collectIsFocusedAsState()
                    val currentContainerColor by with(MaterialTheme.colorScheme) {
                        animateColorAsState(
                            targetValue = when {
                                selected -> inverseSurface
                                focused -> primaryContainer.copy(0.67f)
                                else -> background
                            },
                            label = "scaffold-navigation-container"
                        )
                    }
                    val currentContentColor by with(MaterialTheme.colorScheme) {
                        animateColorAsState(
                            targetValue = when {
                                selected -> inverseOnSurface
                                focused -> onPrimaryContainer
                                else -> onBackground
                            },
                            label = "scaffold-navigation-content"
                        )
                    }
                    Card(
                        onClick = onClick,
                        colors = CardDefaults.colors(
                            containerColor = currentContainerColor,
                            contentColor = currentContentColor
                        ),
                        interactionSource = source,
                        shape = CardDefaults.shape(CircleShape),
                        border = CardDefaults.border(focusedBorder = Border.None),
                        scale = CardDefaults.scale(
                            scale = if (selected) 1.1f else 1f,
                            focusedScale = if (selected) 1.2f else 1.1f
                        ),
                        content = {
                            Box(modifier = Modifier.padding(spacing.medium)) { icon() }
                        }
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxHeight()
                .weight(1f)
        ) {
            TopBarWithContent(
                message = message,
                windowInsets = WindowInsets.systemBars,
                title = title,
                onBackPressed = onBackPressed,
                actions = actions,
                content = content
            )
        }
    }
}