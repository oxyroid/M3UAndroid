package com.m3u.features.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.util.toast
import com.m3u.features.main.components.SubscriptionItem
import com.m3u.features.main.vo.SubscriptionDetail
import com.m3u.ui.local.LocalSpacing
import com.m3u.ui.util.EventEffect

@Composable
internal fun MainRoute(
    navigateToSubscription: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state: MainState by viewModel.readable.collectAsStateWithLifecycle()
    val subscriptions: List<SubscriptionDetail> = state.subscriptions

    EventEffect(state.message) {
        context.toast(it)
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
    navigateToSubscription: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                number = subscriptionWithNumber.count,
                modifier = Modifier.fillParentMaxWidth(),
                onClick = {
                    navigateToSubscription(subscriptionWithNumber.subscription.url)
                }
            )
        }
    }
}