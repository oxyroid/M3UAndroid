package com.m3u.smartphone.ui.business.setting.fragments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.business.setting.ExtensionSettingInputError
import com.m3u.business.setting.ExtensionSettingsState
import com.m3u.business.setting.extensionSettingInputError
import com.m3u.business.setting.normalizedExtensionSettingValue
import com.m3u.data.repository.extension.ExtensionNetworkOriginState
import com.m3u.data.repository.extension.ExtensionSettingEditToken
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingKeys
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.ktx.plus
import com.m3u.smartphone.ui.material.ktx.UiBidiFormatter
import com.m3u.smartphone.ui.material.ktx.rememberUiBidiFormatter
import com.m3u.smartphone.ui.material.ktx.withoutBidiControls
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

@Composable
internal fun ExtensionSettingsScreen(
    state: ExtensionSettingsState,
    extensionId: String,
    onRetry: () -> Unit,
    onUpdate: (
        sectionId: String,
        fieldKey: String,
        editToken: ExtensionSettingEditToken,
        rawValue: String?,
    ) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val stateMatchesDestination = state.extensionId?.value == extensionId
    val configuration = when {
        state is ExtensionSettingsState.Content && stateMatchesDestination ->
            state.configuration
        state is ExtensionSettingsState.Unavailable && stateMatchesDestination -> {
            ExtensionSettingsStatus(
                text = stringResource(string.feat_setting_extension_settings_unavailable),
                tag = "extension-settings-unavailable",
                modifier = modifier,
                contentPadding = contentPadding,
            )
            return
        }
        state is ExtensionSettingsState.Error && stateMatchesDestination -> {
            ExtensionSettingsStatus(
                text = stringResource(string.feat_setting_extension_operation_failed),
                tag = "extension-settings-error",
                modifier = modifier,
                contentPadding = contentPadding,
                onRetry = onRetry,
            )
            return
        }
        else -> {
            ExtensionSettingsLoading(
                modifier = modifier,
                contentPadding = contentPadding,
            )
            return
        }
    }

    val bidiFormatter = rememberUiBidiFormatter()
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
    val validationRequested = remember(configuration.extensionId) {
        mutableStateMapOf<String, Boolean>()
    }
    val dirtyKeys = remember(configuration.extensionId) {
        mutableStateMapOf<String, Boolean>()
    }
    LaunchedEffect(configuration) {
        val activeKeys = mutableSetOf<String>()
        configuration.sections.forEach { section ->
            section.schema.fields.forEach { field ->
                val key = ExtensionSettingKeys.qualified(section.id, field.key)
                activeKeys += key
                if (dirtyKeys[key] != true) {
                    draftValues[key] = if (field.type == ExtensionSettingType.SECRET) {
                        ""
                    } else {
                        configuration.snapshot.values[key].primitiveContent()
                    }
                }
            }
        }
        draftValues.keys.retainAll(activeKeys)
        validationRequested.keys.retainAll(activeKeys)
        dirtyKeys.keys.retainAll(activeKeys)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .testTag("extension-settings"),
        contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (configuration.sections.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(string.feat_setting_extension_settings_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .testTag("extension-settings-empty"),
                )
            }
        }
        configuration.sections.forEach { section ->
            item(key = "section:${section.id}") {
                Text(
                    text = bidiFormatter.natural(section.title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .semantics { heading() },
                )
            }
            items(
                items = section.schema.fields,
                key = { field ->
                    ExtensionSettingKeys.qualified(section.id, field.key)
                },
            ) { field ->
                val key = ExtensionSettingKeys.qualified(section.id, field.key)
                val editToken = checkNotNull(
                    configuration.editToken(section.id, field.key)
                )
                ExtensionSettingControl(
                    field = field,
                    qualifiedKey = key,
                    bidiFormatter = bidiFormatter,
                    rawValue = draftValues[key].orEmpty(),
                    secretConfigured = key in configuration.snapshot.credentialHandles,
                    validationRequested = validationRequested[key] == true,
                    dirty = dirtyKeys[key] == true,
                    updating = key in state.updatingKeys,
                    networkOriginState = configuration.networkOriginState(
                        section.id,
                        field.key,
                    ),
                    onDraftChange = { value ->
                        draftValues[key] = value
                        dirtyKeys[key] = true
                    },
                    onValidationRequested = {
                        validationRequested[key] = true
                    },
                    onUpdate = { value ->
                        validationRequested[key] = false
                        dirtyKeys.remove(key)
                        draftValues[key] = if (
                            field.type == ExtensionSettingType.SECRET
                        ) {
                            ""
                        } else {
                            value.orEmpty()
                        }
                        onUpdate(section.id, field.key, editToken, value)
                    },
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ExtensionSettingsLoading(
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    val loadingDescription = stringResource(string.feat_setting_extension_loading)
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .testTag("extension-settings-loading"),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.semantics {
                contentDescription = loadingDescription
            },
        )
    }
}

@Composable
private fun ExtensionSettingsStatus(
    text: String,
    tag: String,
    modifier: Modifier,
    contentPadding: PaddingValues,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .testTag(tag),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
            )
            onRetry?.let { retry ->
                TextButton(
                    onClick = retry,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("extension-settings-retry"),
                ) {
                    Text(stringResource(string.ui_action_refresh))
                }
            }
        }
    }
}

@Composable
private fun ExtensionSettingControl(
    field: ExtensionSettingField,
    qualifiedKey: String,
    bidiFormatter: UiBidiFormatter,
    rawValue: String,
    secretConfigured: Boolean,
    validationRequested: Boolean,
    dirty: Boolean,
    updating: Boolean,
    networkOriginState: ExtensionNetworkOriginState?,
    onDraftChange: (String) -> Unit,
    onValidationRequested: () -> Unit,
    onUpdate: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val requiredDescription = stringResource(string.feat_setting_provider_error_required)
    val semanticFieldLabelText = extensionSettingSemanticText(
        value = field.label,
        bidiFormatter = bidiFormatter,
    )
    val semanticFieldDescriptionText = field.description
        ?.takeIf(String::isNotBlank)
        ?.let { description ->
            extensionSettingSemanticText(
                value = description,
                bidiFormatter = bidiFormatter,
            )
        }
    val semanticFieldLabel = if (field.required) {
        stringResource(
            string.feat_setting_extension_field_required_description,
            semanticFieldLabelText,
            requiredDescription,
        )
    } else {
        semanticFieldLabelText
    }
    val semanticFieldDescription = listOfNotNull(
        semanticFieldLabel,
        semanticFieldDescriptionText,
    ).joinToString(separator = "\n")
    val inputError = field.extensionSettingInputError(
        rawValue = rawValue,
        secretConfigured = secretConfigured,
    )
    val visibleInputError = inputError.takeIf {
        validationRequested || field.type == ExtensionSettingType.SINGLE_CHOICE
    }
    val inputErrorMessage = visibleInputError?.message()
    val saveLabel = stringResource(string.feat_setting_extension_setting_save)
    val clearLabel = stringResource(string.feat_setting_extension_setting_clear)
    val saveDescription = stringResource(
        string.feat_setting_extension_action_field_description,
        saveLabel,
        semanticFieldLabelText,
    )
    val clearDescription = stringResource(
        string.feat_setting_extension_action_field_description,
        clearLabel,
        semanticFieldLabelText,
    )
    val networkOriginStateLabel = networkOriginState?.let {
        extensionNetworkOriginStateLabel(it)
    }
    val focusManager = LocalFocusManager.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (field.type) {
            ExtensionSettingType.BOOLEAN -> {
                val checked = rawValue.toBooleanStrictOrNull() ?: false
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("extension-setting:$qualifiedKey")
                        .toggleable(
                            value = checked,
                            enabled = !updating,
                            role = Role.Switch,
                            onValueChange = { value -> onUpdate(value.toString()) },
                        )
                        .semantics {
                            contentDescription = semanticFieldDescription
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingLabel(
                        field = field,
                        bidiFormatter = bidiFormatter,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp),
                    )
                    Switch(
                        checked = checked,
                        onCheckedChange = null,
                        enabled = !updating,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }

            ExtensionSettingType.SINGLE_CHOICE -> {
                SettingLabel(field, bidiFormatter)
                FlowRow(
                    modifier = Modifier
                        .selectableGroup()
                        .semantics {
                            inputErrorMessage?.let { message ->
                                error(message)
                            }
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    field.choices.forEach { choice ->
                        val semanticChoiceLabel = extensionSettingSemanticText(
                            value = choice.label,
                            bidiFormatter = bidiFormatter,
                        )
                        val choiceDescription = if (field.required) {
                            stringResource(
                                string.feat_setting_extension_choice_field_required_description,
                                semanticChoiceLabel,
                                semanticFieldLabelText,
                                requiredDescription,
                            )
                        } else {
                            stringResource(
                                string.feat_setting_extension_choice_field_description,
                                semanticChoiceLabel,
                                semanticFieldLabelText,
                            )
                        }
                        val choiceControlDescription = listOfNotNull(
                            choiceDescription,
                            semanticFieldDescriptionText,
                        ).joinToString(separator = "\n")
                        FilterChip(
                            selected = rawValue == choice.value,
                            enabled = !updating,
                            onClick = { onUpdate(choice.value) },
                            label = {
                                Text(
                                    text = bidiFormatter.natural(choice.label),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(
                                    "extension-setting-choice:$qualifiedKey:${choice.value}"
                                )
                                .semantics {
                                    role = Role.RadioButton
                                    contentDescription = choiceControlDescription
                                },
                        )
                    }
                }
                inputErrorMessage?.let { message ->
                    ExtensionSettingErrorText(message)
                }
            }

            ExtensionSettingType.TEXT,
            ExtensionSettingType.NUMBER,
            ExtensionSettingType.SECRET -> {
                val singleLineInput =
                    field.type != ExtensionSettingType.TEXT || field.networkOrigin
                val textDirection = extensionSettingInputTextDirection(field)
                fun commitInput(): Boolean {
                    if (updating) return false
                    onValidationRequested()
                    if (inputError != null) return false
                    onUpdate(
                        rawValue
                            .takeUnless { it.isEmpty() && !field.required }
                            ?.let(field::normalizedExtensionSettingValue)
                    )
                    return true
                }
                SettingLabel(field, bidiFormatter)
                OutlinedTextField(
                    value = rawValue,
                    onValueChange = onDraftChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("extension-setting-field:$qualifiedKey")
                        .semantics {
                            contentDescription = semanticFieldDescription
                            inputErrorMessage?.let { message ->
                                error(message)
                            }
                        },
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
                    textStyle = LocalTextStyle.current.copy(
                        textDirection = textDirection,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (field.type == ExtensionSettingType.NUMBER) {
                            KeyboardType.Decimal
                        } else {
                            KeyboardType.Text
                        },
                        imeAction = if (singleLineInput) {
                            ImeAction.Done
                        } else {
                            ImeAction.Default
                        },
                    ),
                    keyboardActions = if (singleLineInput) {
                        KeyboardActions(
                            onDone = {
                                if (commitInput()) {
                                    focusManager.clearFocus()
                                }
                            }
                        )
                    } else {
                        KeyboardActions.Default
                    },
                    readOnly = updating,
                    isError = inputErrorMessage != null,
                    supportingText = when {
                        inputErrorMessage != null -> {
                            { ExtensionSettingErrorText(inputErrorMessage) }
                        }
                        field.networkOrigin -> {
                            {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    networkOriginStateLabel?.let { stateLabel ->
                                        Text(
                                            text = stateLabel,
                                            color = if (
                                                networkOriginState.requiresUserAttention
                                            ) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier
                                                .testTag(
                                                    "extension-setting-origin-state:$qualifiedKey"
                                                )
                                                .semantics {
                                                    liveRegion = LiveRegionMode.Polite
                                                },
                                        )
                                    }
                                    Text(
                                        stringResource(
                                            string.feat_setting_extension_network_origin_save_notice
                                        )
                                    )
                                }
                            }
                        }
                        else -> null
                    },
                    singleLine = singleLineInput,
                    minLines = 1,
                    maxLines = if (singleLineInput) 1 else 4,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AnimatedVisibility(
                        visible = shouldShowExtensionSettingSaveAction(
                            dirty = dirty,
                            networkOriginState = networkOriginState,
                        )
                    ) {
                        TextButton(
                            enabled = !updating &&
                                (
                                    field.type != ExtensionSettingType.SECRET ||
                                        rawValue.isNotEmpty()
                                    ),
                            onClick = { commitInput() },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("extension-setting-save:$qualifiedKey")
                                .semantics {
                                    contentDescription = saveDescription
                                },
                        ) {
                            Text(saveLabel)
                        }
                    }
                    if (rawValue.isNotEmpty() || secretConfigured) {
                        TextButton(
                            enabled = !updating,
                            onClick = { onUpdate(null) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("extension-setting-clear:$qualifiedKey")
                                .semantics {
                                    contentDescription = clearDescription
                                },
                        ) {
                            Text(clearLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionSettingInputError.message(): String = stringResource(
    when (this) {
        ExtensionSettingInputError.REQUIRED -> string.feat_setting_provider_error_required
        ExtensionSettingInputError.TOO_LONG -> string.feat_setting_provider_error_too_long
        ExtensionSettingInputError.INVALID_NUMBER -> string.feat_setting_provider_error_number
        ExtensionSettingInputError.INVALID_BOOLEAN -> string.feat_setting_provider_error_boolean
        ExtensionSettingInputError.INVALID_CHOICE -> string.feat_setting_provider_error_choice
        ExtensionSettingInputError.INVALID_NETWORK_ORIGIN ->
            string.feat_setting_extension_error_network_origin
    }
)

@Composable
private fun ExtensionSettingErrorText(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.semantics {
            error(message)
            liveRegion = LiveRegionMode.Polite
        },
    )
}

@Composable
private fun SettingLabel(
    field: ExtensionSettingField,
    bidiFormatter: UiBidiFormatter,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = bidiFormatter.natural(
                if (field.required) "${field.label} *" else field.label
            ),
        )
        field.description?.takeIf(String::isNotBlank)?.let { description ->
            Text(
                text = bidiFormatter.natural(description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Any?.primitiveContent(): String = when (this) {
    is JsonPrimitive -> booleanOrNull?.toString() ?: contentOrNull.orEmpty()
    else -> ""
}

internal fun extensionSettingSemanticText(
    value: String,
    bidiFormatter: UiBidiFormatter,
): String = bidiFormatter.natural(value.withoutBidiControls())

internal fun extensionSettingInputTextDirection(
    field: ExtensionSettingField,
): TextDirection = if (
    field.type == ExtensionSettingType.NUMBER ||
        field.networkOrigin
) {
    TextDirection.Ltr
} else {
    TextDirection.ContentOrLtr
}

internal fun shouldShowExtensionSettingSaveAction(
    dirty: Boolean,
    networkOriginState: ExtensionNetworkOriginState?,
): Boolean = dirty ||
    networkOriginState == ExtensionNetworkOriginState.REQUIRES_APPROVAL
