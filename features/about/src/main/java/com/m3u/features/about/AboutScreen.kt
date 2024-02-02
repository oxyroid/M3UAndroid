package com.m3u.features.about

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleStartEffect
import com.m3u.core.util.basic.title
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.model.LocalHazeState
import com.m3u.ui.helper.LocalHelper
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withContext
import dev.chrisbanes.haze.haze

@Composable
internal fun AboutRoute(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val helper = LocalHelper.current
    val title = stringResource(string.feat_about_title)
    LifecycleStartEffect(Unit) {
        helper.title = title.title()
        helper.deep += 1
        onStopOrDispose {
            helper.deep -= 1
        }
    }

    AboutScreen(
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun AboutScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val libs = remember(context) {
        Libs.Builder().withContext(context).build()
    }

    Background {
        LazyColumn(
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .haze(LocalHazeState.current)
                .then(modifier)
        ) {
            items(libs.libraries) { library ->
                ListItem(
                    headlineContent = {
                        Text(library.name)
                    },
                    supportingContent = {
                        Text(
                            text = library.description.orEmpty(),
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingContent = {
                        Text(library.artifactVersion.orEmpty())
                    }
                )
            }
        }
    }
}
