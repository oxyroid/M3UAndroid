package com.m3u.features.crash.screen.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.features.crash.components.FileItem
import com.m3u.features.crash.screen.list.navigation.NavigateToDetail
import com.m3u.ui.components.Background
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun ListScreen(
    navigateToDetail: NavigateToDetail,
    modifier: Modifier = Modifier,
    viewModel: ListViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val logs = state.logs
    Background {
        LazyColumn(
            modifier = modifier.fillMaxSize()
        ) {
            items(logs) { file ->
                FileItem(
                    file = file,
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .clickable {
                            navigateToDetail(file.path)
                        }
                )
            }
        }
    }
}