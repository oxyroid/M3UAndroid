package com.m3u.features.crash.screen.detail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.material.components.Background
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.MonoText

@Composable
internal fun DetailScreen(
    path: String,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel()
) {
    LaunchedEffect(path) {
        viewModel.onEvent(DetailEvent.Init(path))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Background {
        LazyColumn(
            contentPadding = PaddingValues(LocalSpacing.current.medium),
            modifier = modifier
        ) {
            item {
                MonoText(
                    text = state.text,
                    color = LocalContentColor.current
                )
            }
        }
    }
}