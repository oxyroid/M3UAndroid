package com.m3u.smartphone.ui.business.setting.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.m3u.data.database.model.DataSource
import com.m3u.smartphone.ui.material.components.ClickableSelection
import com.m3u.smartphone.ui.material.components.SelectionsDefaults

@Composable
internal fun DataSourceSelection(
    selectedState: MutableState<DataSource>,
    supported: List<DataSource>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, LocalContentColor.current.copy(0.38f), SelectionsDefaults.Shape)
    ) {
        ClickableSelection(
            onClick = { expanded = true },
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(selectedState.value.resId))
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
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(maxWidth),
        ) {
            supported.forEach { current ->
                DropdownMenuItem(
                    text = { Text(stringResource(current.resId)) },
                    trailingIcon = {
                        if (selectedState.value == current) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null
                            )
                        }
                    },
                    enabled = current.supported,
                    onClick = {
                        selectedState.value = current
                        expanded = false
                    }
                )
            }
        }
    }
}
