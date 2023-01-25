package com.m3u.features.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.util.toast
import com.m3u.features.main.vo.SubscriptionState
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
    val subscriptions: List<SubscriptionState> = state.subscriptions

    LaunchedEffect(state.message) {
        state.message.handle {
            context.toast(it)
        }
    }
    MainScreen(
        modifier = modifier,
        subscriptionStates = subscriptions,
        navigateToSubscription = navigateToSubscription
    )
}

@Composable
private fun MainScreen(
    subscriptionStates: List<SubscriptionState>,
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
                items(subscriptionStates) { subscriptionWithNumber ->
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
            Surface(
                color = LocalTheme.current.primary,
                contentColor = LocalTheme.current.onPrimary,
                shape = CircleShape,
                modifier = Modifier.padding(LocalSpacing.current.small)
            ) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.subtitle2,
                    maxLines = 1,
                )
            }
        }
    }
}