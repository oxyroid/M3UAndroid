package com.m3u.smartphone.ui.business.setting.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.components.ClickableSelection
import com.m3u.smartphone.ui.material.components.SelectionsDefaults

internal data class DataSourceSelectionOption(
    val key: String,
    val label: String,
    val supportingLabel: String? = null,
    val enabled: Boolean = true,
) {
    init {
        require(key.isNotBlank())
    }
}

@Composable
internal fun DataSourceSelection(
    selectedKey: String,
    selectedLabel: String,
    options: List<DataSourceSelectionOption>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (!enabled) expanded = false
    }
    val expansionState = stringResource(
        if (expanded) string.ui_state_expanded else string.ui_state_collapsed
    )
    val expansionAction = stringResource(
        if (expanded) string.ui_action_collapse else string.ui_action_expand
    )
    val selectedOption = options.firstOrNull { option -> option.key == selectedKey }
    val displayedLabel = selectedOption?.label ?: selectedLabel
    val displayedSupportingLabel = selectedOption?.supportingLabel
    val accessibleSelection = if (displayedSupportingLabel == null) {
        stringResource(
            string.feat_setting_data_source_selector_description,
            displayedLabel,
        )
    } else {
        stringResource(
            string.feat_setting_data_source_selector_with_identifier_description,
            displayedLabel,
            displayedSupportingLabel,
        )
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, LocalContentColor.current.copy(0.38f), SelectionsDefaults.Shape)
    ) {
        ClickableSelection(
            onClick = { expanded = !expanded },
            enabled = enabled,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = accessibleSelection
                    stateDescription = expansionState
                    role = Role.DropdownList
                    if (enabled) {
                        onClick(label = expansionAction) {
                            expanded = !expanded
                            true
                        }
                    } else {
                        disabled()
                    }
                }
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayedLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                displayedSupportingLabel?.let { supportingLabel ->
                    Text(
                        text = supportingLabel,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Icon(
                imageVector = if (expanded) {
                    Icons.Rounded.KeyboardArrowUp
                } else {
                    Icons.Rounded.KeyboardArrowDown
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(maxWidth)
                .selectableGroup(),
        ) {
            options.forEach { option ->
                val selected = selectedKey == option.key
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            enabled = enabled && option.enabled,
                            role = Role.RadioButton,
                            onClick = {
                                onSelect(option.key)
                                expanded = false
                            },
                        )
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("subscription-source-option-${option.key}"),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(option.label)
                        option.supportingLabel?.let { supportingLabel ->
                            Text(
                                text = supportingLabel,
                                color = LocalContentColor.current.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}
