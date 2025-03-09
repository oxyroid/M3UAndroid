package com.m3u.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.i18n.R.string
import com.m3u.material.components.BottomSheet
import com.m3u.material.model.LocalSpacing

@Immutable
enum class Sort(@StringRes val resId: Int) {
    UNSPECIFIED(string.ui_sort_unspecified),
    ASC(string.ui_sort_asc),
    DESC(string.ui_sort_desc),
    RECENTLY(string.ui_sort_recently),
    MIXED(string.ui_sort_mixed)
}

fun Sort.toCommonSort(): ChannelRepository.Sort = when (this) {
    Sort.UNSPECIFIED -> ChannelRepository.Sort.UNSPECIFIED
    Sort.ASC -> ChannelRepository.Sort.ASC
    Sort.DESC -> ChannelRepository.Sort.DESC
    Sort.RECENTLY -> ChannelRepository.Sort.RECENTLY
    Sort.MIXED -> ChannelRepository.Sort.MIXED
}

@Composable
fun SortBottomSheet(
    visible: Boolean,
    sort: Sort,
    sorts: List<Sort>,
    sheetState: SheetState,
    onChanged: (Sort) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    BottomSheet(
        sheetState = sheetState,
        visible = visible,
        header = {
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
        },
        body = {
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
        },
        onDismissRequest = onDismissRequest,
        modifier = modifier
    )
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
