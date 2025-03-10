package com.m3u.tv.screens.profile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.m3u.tv.screens.dashboard.rememberChildPadding

@Immutable
data class AccountsSectionData(
    val title: String,
    val value: String? = null,
    val onClick: () -> Unit = {}
)

@Composable
fun AccountsSection() {
    val childPadding = rememberChildPadding()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val accountsSectionListItems = remember {
        listOf(
            AccountsSectionData(
                title = "AccountsSelectionSwitchAccountsTitle",
                value = "AccountsSelectionSwitchAccountsEmail"
            ),
            AccountsSectionData(
                title = "AccountsSelectionLogOut",
                value = "AccountsSelectionSwitchAccountsEmail"
            ),
            AccountsSectionData(
                title = "AccountsSelectionChangePasswordTitle",
                value = "AccountsSelectionChangePasswordValue"
            ),
            AccountsSectionData(
                title = "AccountsSelectionAddNewAccountTitle",
            ),
            AccountsSectionData(
                title = "AccountsSelectionViewSubscriptionsTitle"
            ),
            AccountsSectionData(
                title = "AccountsSelectionDeleteAccountTitle",
                onClick = { showDeleteDialog = true }
            )
        )
    }

    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = childPadding.start),
        columns = GridCells.Fixed(2),
        content = {
            items(accountsSectionListItems.size) { index ->
                AccountsSelectionItem(
                    modifier = Modifier.focusRequester(focusRequester),
                    key = index,
                    accountsSectionData = accountsSectionListItems[index]
                )
            }
        }
    )

    AccountsSectionDeleteDialog(
        showDialog = showDeleteDialog,
        onDismissRequest = { showDeleteDialog = false },
        modifier = Modifier.width(428.dp)
    )
}
