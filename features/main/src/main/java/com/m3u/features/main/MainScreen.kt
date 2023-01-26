package com.m3u.features.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.util.toast
import com.m3u.features.main.vo.SubscriptionDetail
import com.m3u.ui.components.basic.M3UBackground
import com.m3u.ui.components.basic.M3URow
import com.m3u.ui.local.LocalSpacing
import com.m3u.ui.local.LocalTheme
import com.m3u.ui.model.Background
import com.m3u.ui.model.LocalBackground

@Composable
internal fun MainRoute(
    navigateToSubscription: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state: MainState by viewModel.readable.collectAsStateWithLifecycle()
    val subscriptions: List<SubscriptionDetail> = state.subscriptions

    LaunchedEffect(state.message) {
        state.message.handle {
            context.toast(it)
        }
    }
    MainScreen(
        modifier = modifier,
        subscriptionDetails = subscriptions,
        navigateToSubscription = navigateToSubscription
    )
}

@Composable
private fun MainScreen(
    subscriptionDetails: List<SubscriptionDetail>,
    navigateToSubscription: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = Background(
        color = LocalTheme.current.topBar
    )
    CompositionLocalProvider(LocalBackground provides background) {
        M3UBackground {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = LocalSpacing.current.medium
                    ),
                contentPadding = PaddingValues(vertical = LocalSpacing.current.medium),
                verticalArrangement = Arrangement.spacedBy(
                    LocalSpacing.current.small,
                )
            ) {
                items(subscriptionDetails) { subscriptionWithNumber ->
                    SubscriptionItem(
                        label = subscriptionWithNumber.subscription.title,
                        number = subscriptionWithNumber.number,
                        modifier = Modifier.fillParentMaxWidth(),
                        onClick = {
                            navigateToSubscription(subscriptionWithNumber.subscription.id)
                        }
                    )
                }
            }

        }
    }
}

@Composable
private fun SubscriptionItem(
    label: String,
    number: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(LocalSpacing.current.medium)
    ) {
        M3URow(
            modifier = modifier.clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple()
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.subtitle2,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(shape = CircleShape)
                    .background(color = LocalTheme.current.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    color = LocalTheme.current.onPrimary,
                    text = number.toString(),
                    style = MaterialTheme.typography.subtitle2,
                    maxLines = 1,
                    modifier = Modifier.padding(
                        start = LocalSpacing.current.extraSmall,
                        end = LocalSpacing.current.extraSmall,
                        bottom = 2.dp,
                    ),
                    softWrap = false,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}