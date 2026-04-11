package com.m3u.smartphone.ui

import android.app.ActivityOptions
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopSearchBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.preferenceOf
import com.m3u.core.wrapper.eventOf
import com.m3u.smartphone.R
import com.m3u.i18n.R as I18nR
import com.m3u.smartphone.ui.business.channel.PlayerActivity
import com.m3u.smartphone.ui.common.AppNavHost
import com.m3u.smartphone.ui.common.internal.Events
import com.m3u.smartphone.ui.material.components.Destination
import com.m3u.smartphone.ui.material.components.SettingDestination
import com.m3u.smartphone.ui.material.components.SnackHost
import com.m3u.smartphone.ui.material.model.LocalSpacing
import kotlinx.coroutines.launch

@Composable
fun App(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    AppImpl(
        navController = navController,
        modifier = modifier
    )
}

@Composable
private fun AppImpl(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current

    val zappingMode by preferenceOf(PreferencesKeys.ZAPPING_MODE)

    val entry by navController.currentBackStackEntryAsState()

    val currentDestination by remember {
        derivedStateOf {
            Destination.of(entry?.destination?.route)
        }
    }
    val isOnSearchTab = currentDestination == Destination.Search
    val isOnRootTab = currentDestination != null
    val isOnPlaylistPage = !isOnRootTab

    val navigateToDestination = { destination: Destination ->
        navController.navigate(destination.name, navOptions {
            popUpTo(destination.name) {
                inclusive = true
            }
        })
    }

    val navigateToChannel: () -> Unit = {
        if (!zappingMode || !PlayerActivity.isInPipMode) {
            val options = ActivityOptions.makeCustomAnimation(
                context,
                0,
                0
            )
            context.startActivity(
                Intent(context, PlayerActivity::class.java),
                options.toBundle()
            )
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Destination.entries.forEach { destination ->
                val isSelected = destination == currentDestination
                item(
                    icon = {
                        Icon(
                            imageVector = when {
                                isSelected -> destination.selectedIcon
                                else -> destination.unselectedIcon
                            },
                            contentDescription = stringResource(destination.iconTextId)
                        )
                    },
                    label = {
                        Text(stringResource(destination.iconTextId))
                    },
                    selected = isSelected,
                    onClick = { navigateToDestination(destination) },
                    alwaysShowLabel = false
                )
            }
        },
        modifier = modifier
    ) {
        Column {
            val coroutineScope = rememberCoroutineScope()
            val searchBarState = rememberSearchBarState()
            val textFieldState = rememberTextFieldState()
            val searchQuery by remember {
                derivedStateOf { textFieldState.text.toString() }
            }

            // Save search text when leaving Search tab, restore when returning
            var savedSearchQuery by remember { mutableStateOf("") }
            LaunchedEffect(isOnSearchTab) {
                if (isOnSearchTab) {
                    if (savedSearchQuery.isNotEmpty()) {
                        textFieldState.edit {
                            replace(0, length, savedSearchQuery)
                        }
                    }
                } else {
                    savedSearchQuery = textFieldState.text.toString()
                    textFieldState.edit { replace(0, length, "") }
                    searchBarState.animateToCollapsed()
                }
            }

            val inputField = @Composable {
                SearchBarDefaults.InputField(
                    searchBarState = searchBarState,
                    textFieldState = textFieldState,
                    onSearch = {
                        // On playlist pages, collapse after search submit
                        if (isOnPlaylistPage) {
                            coroutineScope.launch { searchBarState.animateToCollapsed() }
                        }
                    },
                    placeholder = {
                        Text(
                            if (isOnPlaylistPage) "Filter in playlist..."
                            else "Search..."
                        )
                    },
                    leadingIcon = {
                        if (isOnPlaylistPage && searchBarState.currentValue == SearchBarValue.Expanded) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        textFieldState.edit { replace(0, length, "") }
                                        searchBarState.animateToCollapsed()
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Default.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    },
                    trailingIcon = { Icon(Icons.Default.MoreVert, contentDescription = null) },
                )
            }

            val isOnForyouTab = currentDestination == Destination.Foryou
            val isOnSettingTab = currentDestination == Destination.Setting
            if (isOnForyouTab) {
                // App header banner on Foryou tab
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        navigateToDestination(Destination.Setting)
                        Events.settingDestination = eventOf(SettingDestination.Playlists)
                    }) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "Add playlist"
                        )
                    }
                }
            } else if (isOnSettingTab) {
                Text(
                    text = stringResource(I18nR.string.ui_title_setting),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            } else {
                TopSearchBar(
                    state = searchBarState,
                    inputField = inputField
                )
            }

            AppNavHost(
                navController = navController,
                navigateToDestination = { navController.navigate(it.name) },
                navigateToChannel = navigateToChannel,
                searchQuery = searchQuery,
                onCollapseSearch = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            // snack-host area
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.End),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.medium)
            ) {
                SnackHost(Modifier.weight(1f))
            }
        }
    }
}
