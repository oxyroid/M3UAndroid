package com.m3u.features.favorite

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.data.database.entity.Stream
import com.m3u.features.favorite.components.FavouriteGallery
import com.m3u.material.components.Background
import com.m3u.material.ktx.interceptVolumeEvent
import com.m3u.ui.Action
import com.m3u.ui.EventHandler
import com.m3u.ui.LocalHelper
import com.m3u.ui.ResumeEvent
import com.m3u.ui.SortBottomSheet
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun FavouriteRoute(
    navigateToStream: () -> Unit,
    resume: ResumeEvent,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: FavouriteViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val pref = LocalPref.current

    val streams by viewModel.streams.collectAsStateWithLifecycle()
    val zapping by viewModel.zapping.collectAsStateWithLifecycle()
    val sorts = viewModel.sorts
    val sort by viewModel.sort.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState()

    var isSortSheetVisible by rememberSaveable { mutableStateOf(false) }

    EventHandler(resume) {
        helper.actions = persistentListOf(
            Action(
                icon = Icons.AutoMirrored.Rounded.Sort,
                contentDescription = "sort",
                onClick = { isSortSheetVisible = true }
            )
        )
    }

    val interceptVolumeEventModifier = remember(pref.godMode) {
        if (pref.godMode) {
            Modifier.interceptVolumeEvent { event ->
                pref.rowCount = when (event) {
                    KeyEvent.KEYCODE_VOLUME_UP -> (pref.rowCount - 1).coerceAtLeast(1)
                    KeyEvent.KEYCODE_VOLUME_DOWN -> (pref.rowCount + 1).coerceAtMost(3)
                    else -> return@interceptVolumeEvent
                }
            }
        } else Modifier
    }

    Background {
        SortBottomSheet(
            visible = isSortSheetVisible,
            sort = sort,
            sorts = sorts,
            sheetState = sheetState,
            onChanged = { viewModel.sort(it) },
            onDismissRequest = { isSortSheetVisible = false }
        )
        FavoriteScreen(
            contentPadding = contentPadding,
            rowCount = pref.rowCount,
            streams = streams,
            zapping = zapping,
            navigateToStream = navigateToStream,
            modifier = modifier
                .fillMaxSize()
                .then(interceptVolumeEventModifier)
        )
    }
}

@Composable
private fun FavoriteScreen(
    contentPadding: PaddingValues,
    rowCount: Int,
    streams: ImmutableList<Stream>,
    zapping: Stream?,
    navigateToStream: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val actualRowCount = when (configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> rowCount
        Configuration.ORIENTATION_LANDSCAPE -> rowCount + 2
        else -> rowCount + 2
    }
    FavouriteGallery(
        contentPadding = contentPadding,
        streams = streams,
        zapping = zapping,
        rowCount = actualRowCount,
        navigateToStream = navigateToStream,
        modifier = modifier
    )
}