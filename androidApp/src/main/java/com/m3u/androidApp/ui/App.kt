package com.m3u.androidApp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import com.m3u.androidApp.components.OptimizeBanner
import com.m3u.androidApp.components.PostDialog
import com.m3u.androidApp.components.PostDialogStatus
import com.m3u.androidApp.navigation.BottomNavigationSheet
import com.m3u.androidApp.navigation.Destination
import com.m3u.androidApp.navigation.M3UNavHost
import com.m3u.androidApp.navigation.rootNavigationRoute
import com.m3u.data.database.entity.Post
import com.m3u.features.console.navigation.consoleRoute
import com.m3u.features.feed.navigation.feedRoute
import com.m3u.features.live.navigation.livePlaylistRoute
import com.m3u.features.live.navigation.liveRoute
import com.m3u.ui.components.AppTopBar
import com.m3u.ui.components.Background
import com.m3u.ui.components.IconButton

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun App(
    appState: AppState = rememberAppState(),
    viewModel: RootViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    val post = state.post
    val onPost = { it: Post? -> viewModel.onEvent(RootEvent.OnPost(it)) }
    LaunchedEffect(Unit) {
        viewModel.onEvent(RootEvent.OnInitialTab)
    }
    LaunchedEffect(state.navigateTopLevelDestination) {
        state.navigateTopLevelDestination.handle {
            appState.navigateToTopLevelDestination(it)
        }
    }
    Background {
        val currentDestination = appState.currentComposableNavDestination
        val topLevelTitle = appState.currentComposableTopLevelDestination
            ?.titleTextId
            ?.let { stringResource(it) }
        val title by appState.title.collectAsStateWithLifecycle()
        val text by remember(topLevelTitle) {
            derivedStateOf { topLevelTitle ?: title }
        }
        var postDialogStatus = remember(post, posts) {
            if (post == null || post.temporal) PostDialogStatus.Idle
            else {
                if (post.temporal) PostDialogStatus.Idle
                else {
                    val index = posts.indexOf(post)
                    val total = posts.size
                    if (index != -1 && total > 0) {
                        PostDialogStatus.Visible(
                            post = post,
                            index = posts.indexOf(post),
                            total = posts.size
                        )
                    } else {
                        onPost(null)
                        PostDialogStatus.Idle
                    }
                }
            }
        }
        val isSystemBarVisible =
            !appState.currentComposableNavDestination.isInFullscreenDestination()
        AppTopBar(
            text = text,
            visible = isSystemBarVisible,
            scrollable = !currentDestination.isInDestination<Destination.Root>(),
            actions = {
                val actions by appState.actions.collectAsStateWithLifecycle()
                actions.forEach { action ->
                    IconButton(
                        icon = action.icon,
                        contentDescription = action.contentDescription,
                        onClick = action.onClick
                    )
                }
            },
            onBackPressed = if (!appState.currentComposableNavDestination.isInLeafDestination()) null
            else appState::onBackClick
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                M3UNavHost(
                    navController = appState.navController,
                    pagerState = appState.pagerState,
                    destinations = appState.topLevelDestinations,
                    navigateToDestination = appState::navigateToDestination,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                AnimatedVisibility(isSystemBarVisible) {
                    Column {
                        OptimizeBanner(
                            posts = posts,
                            onPost = onPost,
                            modifier = Modifier.fillMaxWidth()
                        )
                        BottomNavigationSheet(
                            destinations = appState.topLevelDestinations,
                            currentTopLevelDestination = appState.currentComposableTopLevelDestination,
                            navigateToTopLevelDestination = appState::navigateToTopLevelDestination,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        PostDialog(
            status = postDialogStatus,
            onDismiss = { onPost(null) },
            onNext = { viewModel.onEvent(RootEvent.OnNext) },
            onPrevious = { viewModel.onEvent(RootEvent.OnPrevious) },
            onRead = { viewModel.onEvent(RootEvent.OnRead) }
        )
        BackHandler(postDialogStatus != PostDialogStatus.Idle) {
            postDialogStatus = PostDialogStatus.Idle
        }
    }
}

inline fun <reified D : Destination> NavDestination?.isInDestination(): Boolean {
    val targetRoute = when (D::class.java.name) {
        Destination.Live::class.java.name -> liveRoute
        Destination.LivePlayList::class.java.name -> livePlaylistRoute
        Destination.Feed::class.java.name -> feedRoute
        Destination.Console::class.java.name -> consoleRoute
        Destination.Root::class.java.name -> rootNavigationRoute
        else -> return false
    }
    return this?.route == targetRoute
}

fun NavDestination?.isInLeafDestination(): Boolean = isInDestination<Destination.Feed>() ||
        isInDestination<Destination.Live>() ||
        isInDestination<Destination.LivePlayList>() ||
        isInDestination<Destination.Console>()

fun NavDestination?.isInFullscreenDestination(): Boolean = isInDestination<Destination.Live>() ||
        isInDestination<Destination.LivePlayList>()