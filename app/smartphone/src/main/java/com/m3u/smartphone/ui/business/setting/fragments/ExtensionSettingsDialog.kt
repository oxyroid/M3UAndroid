package com.m3u.smartphone.ui.business.setting.fragments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.m3u.data.repository.extension.ExtensionSettingEditToken
import com.m3u.data.repository.extension.ExtensionSettingsConfiguration
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingKeys
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.i18n.R.string
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

@Composable
internal fun ExtensionSettingsDialog(
    configuration: ExtensionSettingsConfiguration,
    onDismiss: () -> Unit,
    onUpdate: (
        sectionId: String,
        fieldKey: String,
        editToken: ExtensionSettingEditToken,
        rawValue: String?,
    ) -> Unit,
) {
    val draftValues = remember(configuration.extensionId) {
        mutableStateMapOf<String, String>().apply {
            configuration.sections.forEach { section ->
                section.schema.fields.forEach { field ->
                    val key = ExtensionSettingKeys.qualified(section.id, field.key)
                    if (field.type != ExtensionSettingType.SECRET) {
                        put(key, configuration.snapshot.values[key].primitiveContent())
                    }
                }
            }
        }
    }
    LaunchedEffect(configuration) {
        val activeKeys = mutableSetOf<String>()
        configuration.sections.forEach { section ->
            section.schema.fields.forEach { field ->
                val key = ExtensionSettingKeys.qualified(section.id, field.key)
                activeKeys += key
                draftValues[key] = if (field.type == ExtensionSettingType.SECRET) {
                    ""
                } else {
                    configuration.snapshot.values[key].primitiveContent()
                }
            }
        }
        draftValues.keys.retainAll(activeKeys)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(string.feat_setting_extension_settings)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (configuration.sections.isEmpty()) {
                    Text(stringResource(string.feat_setting_extension_settings_empty))
                }
                configuration.sections.forEach { section ->
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                    section.schema.fields.forEach { field ->
                        val key = ExtensionSettingKeys.qualified(section.id, field.key)
                        val editToken = checkNotNull(
                            configuration.editToken(section.id, field.key)
                        )
                        ExtensionSettingControl(
                            field = field,
                            rawValue = draftValues[key].orEmpty(),
                            secretConfigured = key in configuration.snapshot.credentialHandles,
                            onDraftChange = { value -> draftValues[key] = value },
                            onUpdate = { value ->
                                draftValues[key] = if (
                                    field.type == ExtensionSettingType.SECRET
                                ) {
                                    ""
                                } else {
                                    value.orEmpty()
                                }
                                onUpdate(section.id, field.key, editToken, value)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
private fun ExtensionSettingControl(
    field: ExtensionSettingField,
    rawValue: String,
    secretConfigured: Boolean,
    onDraftChange: (String) -> Unit,
    onUpdate: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (field.type) {
            ExtensionSettingType.BOOLEAN -> {
                val checked = rawValue.toBooleanStrictOrNull() ?: false
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = checked,
                            role = Role.Switch,
                            onValueChange = { value -> onUpdate(value.toString()) },
                        )
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingLabel(
                        field = field,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp),
                    )
                    Switch(
                        checked = checked,
                        onCheckedChange = null,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }

            ExtensionSettingType.SINGLE_CHOICE -> {
                SettingLabel(field)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    field.choices.forEach { choice ->
                        FilterChip(
                            selected = rawValue == choice.value,
                            onClick = { onUpdate(choice.value) },
                            label = { Text(choice.label) },
                        )
                    }
                }
            }

            ExtensionSettingType.TEXT,
            ExtensionSettingType.NUMBER,
            ExtensionSettingType.SECRET -> {
                OutlinedTextField(
                    value = rawValue,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { SettingLabel(field) },
                    placeholder = if (field.type == ExtensionSettingType.SECRET && secretConfigured) {
                        { Text(stringResource(string.feat_setting_extension_secret_configured)) }
                    } else {
                        null
                    },
                    visualTransformation = if (field.type == ExtensionSettingType.SECRET) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (field.type == ExtensionSettingType.NUMBER) {
                            KeyboardType.Decimal
                        } else {
                            KeyboardType.Text
                        },
                    ),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = field.type != ExtensionSettingType.SECRET || rawValue.isNotEmpty(),
                        onClick = { onUpdate(rawValue) },
                    ) {
                        Text(stringResource(string.feat_setting_extension_setting_save))
                    }
                    if (rawValue.isNotEmpty() || secretConfigured) {
                        TextButton(onClick = { onUpdate(null) }) {
                            Text(stringResource(string.feat_setting_extension_setting_clear))
                        }
                    }
                }
                if (field.networkOrigin) {
                    Text(
                        text = stringResource(
                            string.feat_setting_extension_network_origin_save_notice
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        field.description?.let { description ->
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingLabel(
    field: ExtensionSettingField,
    modifier: Modifier = Modifier,
) {
    Text(
        text = if (field.required) "${field.label} *" else field.label,
        modifier = modifier,
    )
}

private fun Any?.primitiveContent(): String = when (this) {
    is JsonPrimitive -> booleanOrNull?.toString() ?: contentOrNull.orEmpty()
    else -> ""
}
