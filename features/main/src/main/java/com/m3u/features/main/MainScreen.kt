package com.m3u.features.main

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.m3u.core.util.context.toast
import com.m3u.features.main.components.SubscriptionItem
import com.m3u.features.main.model.SubDetail
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.AppAction
import com.m3u.ui.model.SetActions
import com.m3u.ui.util.EventHandler
import com.m3u.ui.util.LifecycleEffect

@Composable
internal fun MainRoute(
    navigateToSubscription: (String, label: String?) -> Unit,
    setAppActions: SetActions,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state: MainState by viewModel.state.collectAsStateWithLifecycle()
    val subscriptions: List<SubDetail> = state.details

    EventHandler(state.message) {
        context.toast(it)
    }
    val currentSetAppActions by rememberUpdatedState(setAppActions)
    LifecycleEffect { event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                val actions = listOf<AppAction>()
                currentSetAppActions(actions)
            }

            Lifecycle.Event.ON_PAUSE -> {
                currentSetAppActions(emptyList())
            }

            else -> {}
        }
    }

    MainScreen(
        modifier = modifier,
        details = subscriptions,
        navigateToSubscription = navigateToSubscription
    )
}

@Composable
private fun MainScreen(
    details: List<SubDetail>,
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
                items(details) { detail ->
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
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(LocalSpacing.current.medium),
                verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.medium),
                horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.medium),
                modifier = modifier.fillMaxSize()
            ) {
                items(details) { detail ->
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