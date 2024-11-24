package com.m3u.feature.setting.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.m3u.data.database.model.DataSource
import com.m3u.material.components.ClickableSelection
import com.m3u.material.components.Icon
import com.m3u.material.components.SelectionsDefaults
import com.m3u.material.ktx.composableOf

@Composable
internal fun DataSourceSelection(
    currentDSource: MutableState<DataSource>,
    dSources: List<DataSource>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.TopStart)
            .border(1.dp, LocalContentColor.current.copy(0.38f), SelectionsDefaults.Shape)
            .then(modifier)
    ) {
        ClickableSelection(
            onClick = { expanded = true },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                when (val source = currentDSource.value) {
                    is DataSource.Ext -> source.label
                    else -> stringResource(source.resId)
                }
            )
            Icon(
                imageVector = if (expanded) {
                    Icons.Rounded.KeyboardArrowUp
                } else {
                    Icons.Rounded.KeyboardArrowDown
                },
                contentDescription = null
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            dSources.forEach { source ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (source) {
                                is DataSource.Ext -> source.label
                                else -> stringResource(source.resId)
                            }
                        )
                    },
                    leadingIcon = composableOf(source is DataSource.Ext) {
                        Icon(
                            imageVector = Icons.Rounded.Extension,
                            contentDescription = null
                        )
                    },
                    trailingIcon = composableOf(currentDSource.value == source) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null
                        )
                    },
                    enabled = source.supported,
                    onClick = {
                        currentDSource.value = source
                        expanded = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}