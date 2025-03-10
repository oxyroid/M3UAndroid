package com.m3u.smartphone.ui.business.crash.screen.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.m3u.smartphone.ui.material.components.Background
import com.m3u.smartphone.ui.business.crash.components.FileItem
import com.m3u.smartphone.ui.business.crash.screen.list.navigation.NavigateToDetail

@Composable
internal fun ListScreen(
    navigateToDetail: NavigateToDetail,
    modifier: Modifier = Modifier,
    viewModel: ListViewModel = hiltViewModel()
) {
    val files = viewModel.files
    Background {
        LazyColumn(
            modifier = modifier.fillMaxSize()
        ) {
            items(files) { file ->
                FileItem(
                    file = file,
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .clickable {
                            navigateToDetail(file.absolutePath)
                        }
                )
            }
        }
    }
}
