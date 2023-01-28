package com.m3u.features.main

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.util.toast
import com.m3u.features.main.components.SubscriptionItem
import com.m3u.features.main.vo.SubscriptionDetail
import com.m3u.ui.local.LocalSpacing
import com.m3u.ui.model.AppAction
import com.m3u.ui.util.EventEffect
import com.m3u.ui.util.LifecycleEffect

@Composable
internal fun MainRoute(
    navigateToSubscription: (String, label: String?) -> Unit,
    setAppActions: (List<AppAction>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state: MainState by viewModel.readable.collectAsStateWithLifecycle()
    val subscriptions: List<SubscriptionDetail> = state.subscriptions

    EventEffect(state.message) {
        context.toast(it)
    }
    val setAppActionsUpdated by rememberUpdatedState(setAppActions)
    LifecycleEffect { event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                val actions = listOf<AppAction>()
                setAppActionsUpdated(actions)
            }
            Lifecycle.Event.ON_PAUSE -> {
                setAppActionsUpdated(emptyList())
            }
            else -> {}
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
    navigateToSubscription: (String, label: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    when (configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(LocalSpacing.current.medium),
                verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.small)
            ) {
                items(subscriptionDetails) { detail ->
                    SubscriptionItem(
                        label = detail.subscription.title,
                        number = detail.count,
                        modifier = Modifier.fillParentMaxWidth(),
                        onClick = {
                            navigateToSubscription(
                                detail.subscription.url,
                                detail.subscription.title
                            )
                        }
                    )
                }
            }
        }
        Configuration.ORIENTATION_LANDSCAPE -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(LocalSpacing.current.medium),
                verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.small),
                horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.small),
                modifier = modifier.fillMaxSize()
            ) {
                items(subscriptionDetails) { detail ->
                    SubscriptionItem(
                        label = detail.subscription.title,
                        number = detail.count,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            navigateToSubscription(
                                detail.subscription.url,
                                detail.subscription.title
                            )
                        }
                    )
                }
            }
        }
        else -> {}
    }
}