package com.m3u.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.ListItemDefaults
import com.m3u.i18n.R.string
import com.m3u.material.components.television.dialogFocusable
import com.m3u.material.model.LocalSpacing
import kotlinx.collections.immutable.ImmutableList

@Immutable
enum class Sort(@StringRes val resId: Int) {
    UNSPECIFIED(string.ui_sort_unspecified),
    ASC(string.ui_sort_asc),
    DESC(string.ui_sort_desc),
    RECENTLY(string.ui_sort_recently)
}

@Composable
fun SortBottomSheet(
    visible: Boolean,
    sort: Sort,
    sorts: ImmutableList<Sort>,
    sheetState: SheetState,
    onChanged: (Sort) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    if (visible) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            windowInsets = WindowInsets(0)
        ) {
            Column(
                modifier = Modifier.padding(WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                    modifier = Modifier.padding(
                        horizontal = spacing.medium
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = "sort"
                    )
                    Text(
                        text = stringResource(string.ui_sort),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(spacing.small),
                    modifier = Modifier
                        .selectableGroup()
                        .padding(spacing.medium)
                ) {
                    sorts.forEach { current ->
                        SortBottomSheetItem(
                            sort = current,
                            selected = current == sort,
                            onSelected = { onChanged(current) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortBottomSheetItem(
    sort: Sort,
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        enabled = selected,
        onClick = {}
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(sort.resId),
                    fontWeight = FontWeight.SemiBold
                )
            },
            modifier = Modifier
                .selectable(
                    selected = selected,
                    role = Role.DropdownList,
                    onClick = onSelected
                )
                .then(modifier)
        )
    }
}

@Composable
fun SortFullScreenDialog(
    visible: Boolean,
    sort: Sort,
    sorts: ImmutableList<Sort>,
    onChanged: (Sort) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(Modifier.fillMaxSize().then(modifier)) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.4f)
                .align(Alignment.CenterEnd)
        ) {
            TvLazyColumn(
                Modifier
                    .fillMaxHeight()
                    .background(androidx.tv.material3.MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
                    .selectableGroup()
                    .dialogFocusable(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sorts) { currentSort ->
                    DenseListItem(
                        selected = currentSort == sort,
                        onClick = { onChanged(currentSort) },
                        leadingContent = {},
                        scale = ListItemDefaults.scale(0.95f, 1f)
                    ) {
                        androidx.tv.material3.Text(currentSort.name)
                    }
                }
            }
            BackHandler {
                onDismissRequest()
            }
        }
    }
}