package com.m3u.business.setting

import com.m3u.data.repository.provider.ProviderAccountSummary
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionSettingChoice
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingSchema
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor
import com.m3u.extension.api.subscription.SubscriptionProviderVariant
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProviderSubscriptionFormTest {
    @Test
    fun `build applies defaults and omits unset optional fields`() {
        var stagedSecrets = 0
        val form = ProviderSubscriptionForm.create(descriptor(requireSecret = false), KIND_ALPHA)
            .update("optional_text", "")
            .update("optional_secret", "")

        val result = form.buildRequest("My provider") { secret ->
            stagedSecrets += 1
            CredentialHandle("handle-$secret")
        }

        val request = assertIs<ProviderSubscriptionFormBuildResult.Ready>(result).request
        assertEquals(PROVIDER_ID, request.providerId)
        assertEquals(KIND_ALPHA, request.providerKind)
        assertEquals(
            mapOf(
                "required_text" to "default-user",
                "number" to "12.5",
                "boolean" to "false",
                "choice" to "auto",
            ),
            request.settingValues,
        )
        assertEquals(emptyMap(), request.credentialHandles)
        assertEquals(0, stagedSecrets)
    }

    @Test
    fun `localized decimal input is stored in canonical wire format`() {
        val form = ProviderSubscriptionForm.create(
            descriptor(requireSecret = false),
            KIND_ALPHA,
        ).update("number", " 12,5 ")

        val request = assertIs<ProviderSubscriptionFormBuildResult.Ready>(
            form.buildRequest("My provider") { CredentialHandle("unused") }
        ).request

        assertEquals("12.5", request.settingValues["number"])
    }

    @Test
    fun `required field falls back to its default after blank input`() {
        val form = ProviderSubscriptionForm.create(descriptor(requireSecret = false), KIND_ALPHA)
            .update("required_text", "   ")

        val request = assertIs<ProviderSubscriptionFormBuildResult.Ready>(
            form.buildRequest("My provider") { CredentialHandle("unused") }
        ).request

        assertEquals("default-user", request.settingValues["required_text"])
    }

    @Test
    fun `invalid fields prevent secret staging and report each field`() {
        var stagedSecrets = 0
        val form = ProviderSubscriptionForm.create(descriptor(), KIND_ALPHA)
            .update("number", "not-a-number")
            .update("boolean", "yes")
            .update("choice", "missing")
            .update("required_secret", "private")

        val result = assertIs<ProviderSubscriptionFormBuildResult.Invalid>(
            form.buildRequest("My provider") {
                stagedSecrets += 1
                CredentialHandle("must-not-be-created")
            }
        )

        assertEquals(0, stagedSecrets)
        assertEquals(ProviderSettingFieldError.INVALID_NUMBER, result.form.error("number"))
        assertEquals(ProviderSettingFieldError.INVALID_BOOLEAN, result.form.error("boolean"))
        assertEquals(ProviderSettingFieldError.INVALID_CHOICE, result.form.error("choice"))
        assertNull(result.form.error("required_secret"))
    }

    @Test
    fun `required empty field is reported only after submission`() {
        val form = ProviderSubscriptionForm.create(descriptor(), KIND_ALPHA)

        assertNull(form.error("required_secret"))
        val invalid = assertIs<ProviderSubscriptionFormBuildResult.Invalid>(
            form.buildRequest("My provider") { CredentialHandle("unused") }
        )
        assertEquals(ProviderSettingFieldError.REQUIRED, invalid.form.error("required_secret"))
    }

    @Test
    fun `valid secret is staged exactly once after validation`() {
        val staged = mutableListOf<String>()
        val form = ProviderSubscriptionForm.create(descriptor(), KIND_ALPHA)
            .update("required_secret", "private value")

        val request = assertIs<ProviderSubscriptionFormBuildResult.Ready>(
            form.buildRequest("My provider") { secret ->
                staged += secret
                CredentialHandle("credential-1")
            }
        ).request

        assertEquals(listOf("private value"), staged)
        assertEquals(
            CredentialHandle("credential-1"),
            request.credentialHandles["required_secret"],
        )
        assertFalse("required_secret" in request.settingValues)
        assertFalse("private value" in request.settingValues.values)
    }

    @Test
    fun `overlong text and secret fail before any credential is staged`() {
        var stagedSecrets = 0
        val form = ProviderSubscriptionForm.create(descriptor(), KIND_ALPHA)
            .update("optional_text", "t".repeat(4_097))
            .update("required_secret", "s".repeat(4_097))

        val invalid = assertIs<ProviderSubscriptionFormBuildResult.Invalid>(
            form.buildRequest("My provider") {
                stagedSecrets += 1
                CredentialHandle("must-not-be-created")
            }
        )

        assertEquals(ProviderSettingFieldError.TOO_LONG, invalid.form.error("optional_text"))
        assertEquals(ProviderSettingFieldError.TOO_LONG, invalid.form.error("required_secret"))
        assertEquals(0, stagedSecrets)
    }

    @Test
    fun `reauthentication prefills only stable non-secret account fields`() {
        val descriptor = reauthenticationDescriptor()
        val account = ProviderAccountSummary(
            playlistTitle = "Living room",
            playlistUrl = "m3u-provider://account/one/live",
            providerId = PROVIDER_ID,
            providerKind = KIND_ALPHA,
            baseUrl = "https://media.example.test",
            username = "viewer",
            serverName = "Home server",
            requiresReauthentication = true,
        )

        val form = ProviderSubscriptionForm.createForReauthentication(descriptor, account)

        assertFalse(form.matchesNewSubscription(descriptor, account.providerKind))
        assertTrue(
            ProviderSubscriptionForm.create(descriptor, account.providerKind)
                .matchesNewSubscription(descriptor, account.providerKind)
        )
        assertEquals(account.playlistUrl, form.reauthenticationPlaylistUrl)
        assertEquals(account.baseUrl, form.field("base_url").value)
        assertEquals(account.username, form.field("username").value)
        assertNull(form.field("password").value)
        assertNull(form.field("provider_option").value)
        var stagedSecrets = 0
        val invalid = assertIs<ProviderSubscriptionFormBuildResult.Invalid>(
            form.buildRequest(account.playlistTitle) {
                stagedSecrets += 1
                CredentialHandle("must-not-be-created")
            }
        )
        assertEquals(ProviderSettingFieldError.REQUIRED, invalid.form.error("password"))
        assertEquals(0, stagedSecrets)
    }

    @Test
    fun `hidden compatibility kind remains usable for account reauthentication`() {
        val descriptor = reauthenticationDescriptor().copy(
            variants = listOf(
                SubscriptionProviderVariant(KIND_ALPHA, "Alpha"),
                SubscriptionProviderVariant(
                    kind = KIND_LEGACY,
                    displayName = "Legacy",
                    userSelectable = false,
                ),
            )
        )
        val account = ProviderAccountSummary(
            playlistTitle = "Restored account",
            playlistUrl = "m3u-provider://account/legacy/live",
            providerId = PROVIDER_ID,
            providerKind = KIND_LEGACY,
            baseUrl = "https://media.example.test",
            username = "viewer",
            serverName = "Home server",
            requiresReauthentication = true,
        )

        val form = ProviderSubscriptionForm.createForReauthentication(descriptor, account)
            .update("password", "new secret")
        val request = assertIs<ProviderSubscriptionFormBuildResult.Ready>(
            form.buildRequest(account.playlistTitle) { CredentialHandle("credential") }
        ).request

        assertEquals(KIND_LEGACY, request.providerKind)
        assertEquals(account.playlistUrl, request.reauthenticationPlaylistUrl)
    }

    @Test
    fun `creating another provider or kind starts with a clean schema state`() {
        val edited = ProviderSubscriptionForm.create(descriptor(), KIND_ALPHA)
            .update("optional_text", "old value")
        val changedKind = ProviderSubscriptionForm.create(descriptor(), KIND_BETA)
        val changedProvider = ProviderSubscriptionForm.create(
            descriptor(id = ExtensionId("com.example.second")),
            KIND_ALPHA,
        )

        assertEquals("old value", edited.field("optional_text").value)
        assertNull(changedKind.field("optional_text").value)
        assertNull(changedProvider.field("optional_text").value)
        assertEquals(KIND_BETA, changedKind.providerKind)
        assertEquals(ExtensionId("com.example.second"), changedProvider.providerId)
    }

    @Test
    fun `localized descriptor refresh preserves compatible input and account binding`() {
        val original = ProviderSubscriptionForm.create(descriptor(), KIND_ALPHA)
            .update("optional_text", "keep me")
            .copy(reauthenticationPlaylistUrl = "m3u-provider://account/one/live")
        val localized = descriptor().copy(
            displayName = "示例",
            settingsSchema = descriptor().settingsSchema?.copy(
                fields = descriptor().settingsSchema!!.fields.map { field ->
                    field.copy(label = "本地化 ${field.key}")
                }
            ),
        )

        val updated = original.updateDescriptor(localized)

        assertEquals("keep me", updated.field("optional_text").input)
        assertEquals("本地化 optional_text", updated.field("optional_text").definition.label)
        assertEquals(original.reauthenticationPlaylistUrl, updated.reauthenticationPlaylistUrl)
    }

    @Test
    fun `schema version change clears all prior input including secrets`() {
        val original = ProviderSubscriptionForm.create(descriptor(), KIND_ALPHA)
            .update("optional_text", "old meaning")
            .update("required_secret", "private")
            .copy(reauthenticationPlaylistUrl = "m3u-provider://account/one/live")
        val changed = descriptor().copy(
            settingsSchema = descriptor().settingsSchema?.copy(version = 4),
        )

        val updated = original.updateDescriptor(changed)

        assertNull(updated.field("optional_text").input)
        assertNull(updated.field("required_secret").input)
        assertEquals(original.reauthenticationPlaylistUrl, updated.reauthenticationPlaylistUrl)
        assertEquals(4, updated.schemaVersion)
    }

    @Test
    fun `same schema field type change cannot reinterpret a secret as plain text`() {
        val original = ProviderSubscriptionForm.create(descriptor(), KIND_ALPHA)
            .update("required_secret", "private value")
        val changed = descriptor().copy(
            settingsSchema = descriptor().settingsSchema?.copy(
                fields = descriptor().settingsSchema!!.fields.map { field ->
                    if (field.key == "required_secret") {
                        field.copy(
                            type = ExtensionSettingType.TEXT,
                            required = false,
                        )
                    } else {
                        field
                    }
                }
            ),
        )

        val updated = original.updateDescriptor(changed)
        val request = assertIs<ProviderSubscriptionFormBuildResult.Ready>(
            updated.buildRequest("My provider") { CredentialHandle("unused") }
        ).request

        assertNull(updated.field("required_secret").input)
        assertFalse("required_secret" in request.settingValues)
        assertFalse("required_secret" in request.credentialHandles)
        assertFalse("private value" in request.settingValues.values)
    }

    private fun descriptor(
        id: ExtensionId = PROVIDER_ID,
        requireSecret: Boolean = true,
    ): SubscriptionProviderDescriptor = SubscriptionProviderDescriptor(
        providerId = id,
        displayName = "Example",
        variants = listOf(
            SubscriptionProviderVariant(KIND_ALPHA, "Alpha"),
            SubscriptionProviderVariant(KIND_BETA, "Beta"),
        ),
        settingsSchema = ExtensionSettingSchema(
            version = 3,
            fields = listOf(
                ExtensionSettingField(
                    key = "required_text",
                    label = "User",
                    type = ExtensionSettingType.TEXT,
                    required = true,
                    defaultValue = JsonPrimitive("default-user"),
                ),
                ExtensionSettingField(
                    key = "optional_text",
                    label = "Note",
                    type = ExtensionSettingType.TEXT,
                ),
                ExtensionSettingField(
                    key = "optional_secret",
                    label = "Optional secret",
                    type = ExtensionSettingType.SECRET,
                ),
                ExtensionSettingField(
                    key = "optional_number",
                    label = "Optional number",
                    type = ExtensionSettingType.NUMBER,
                ),
                ExtensionSettingField(
                    key = "optional_boolean",
                    label = "Optional boolean",
                    type = ExtensionSettingType.BOOLEAN,
                ),
                ExtensionSettingField(
                    key = "optional_choice",
                    label = "Optional choice",
                    type = ExtensionSettingType.SINGLE_CHOICE,
                    choices = listOf(
                        ExtensionSettingChoice("one", "One"),
                        ExtensionSettingChoice("two", "Two"),
                    ),
                ),
                ExtensionSettingField(
                    key = "required_secret",
                    label = "Required secret",
                    type = ExtensionSettingType.SECRET,
                    required = requireSecret,
                ),
                ExtensionSettingField(
                    key = "number",
                    label = "Number",
                    type = ExtensionSettingType.NUMBER,
                    required = true,
                    defaultValue = JsonPrimitive(12.5),
                ),
                ExtensionSettingField(
                    key = "boolean",
                    label = "Boolean",
                    type = ExtensionSettingType.BOOLEAN,
                    required = true,
                    defaultValue = JsonPrimitive(false),
                ),
                ExtensionSettingField(
                    key = "choice",
                    label = "Choice",
                    type = ExtensionSettingType.SINGLE_CHOICE,
                    required = true,
                    choices = listOf(
                        ExtensionSettingChoice("auto", "Automatic"),
                        ExtensionSettingChoice("manual", "Manual"),
                    ),
                    defaultValue = JsonPrimitive("auto"),
                ),
            ),
        ),
    )

    private fun reauthenticationDescriptor(): SubscriptionProviderDescriptor =
        SubscriptionProviderDescriptor(
            providerId = PROVIDER_ID,
            displayName = "Reauthentication provider",
            variants = listOf(SubscriptionProviderVariant(KIND_ALPHA, "Alpha")),
            settingsSchema = ExtensionSettingSchema(
                version = 1,
                fields = listOf(
                    ExtensionSettingField(
                        key = "base_url",
                        label = "Server",
                        type = ExtensionSettingType.TEXT,
                        required = true,
                    ),
                    ExtensionSettingField(
                        key = "username",
                        label = "Username",
                        type = ExtensionSettingType.TEXT,
                        required = true,
                    ),
                    ExtensionSettingField(
                        key = "password",
                        label = "Password",
                        type = ExtensionSettingType.SECRET,
                        required = true,
                    ),
                    ExtensionSettingField(
                        key = "provider_option",
                        label = "Provider option",
                        type = ExtensionSettingType.TEXT,
                    ),
                ),
            ),
        )

    private fun ProviderSubscriptionForm.field(key: String): ProviderSubscriptionFormField =
        fields.single { field -> field.definition.key == key }

    private fun ProviderSubscriptionForm.error(key: String): ProviderSettingFieldError? =
        field(key).error

    private companion object {
        val PROVIDER_ID = ExtensionId("com.example.provider")
        val KIND_ALPHA = ProviderKind("alpha")
        val KIND_BETA = ProviderKind("beta")
        val KIND_LEGACY = ProviderKind("legacy")
    }
}
