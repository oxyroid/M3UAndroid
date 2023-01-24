package com.m3u.features.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.features.main.vo.SubscriptionVO
import com.m3u.ui.components.basic.M3UBackground
import com.m3u.ui.components.basic.M3UColumn
import com.m3u.ui.local.LocalSpacing
import com.m3u.ui.local.LocalTheme
import com.m3u.ui.model.Background
import com.m3u.ui.model.LocalBackground

@Composable
internal fun MainRoute(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val state: MainState by viewModel.readable.collectAsStateWithLifecycle()
    val subscriptions: List<SubscriptionVO> = when (state) {
        MainState.Loading -> emptyList()
        is MainState.Success -> (state as MainState.Success).subscriptions
    }
    MainScreen(
        modifier = modifier,
        subscriptions = subscriptions
    )
}

@Composable
private fun MainScreen(
    modifier: Modifier = Modifier,
    subscriptions: List<SubscriptionVO>
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
                items(subscriptions) { subscription ->
                    SubscriptionItem(
                        label = subscription.label,
                        count = subscription.count,
                        preview = subscription.preview,
                        modifier = Modifier.fillParentMaxWidth(),
                        onClick = {

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
    modifier: Modifier = Modifier,
    count: Int = 0,
    preview: String = "",
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(LocalSpacing.current.medium)
    ) {
        M3UColumn(
            modifier = modifier.clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple()
            )
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.subtitle2
            )
            Text(
                text = count.coerceAtLeast(0).toString()
            )
        }
    }
}