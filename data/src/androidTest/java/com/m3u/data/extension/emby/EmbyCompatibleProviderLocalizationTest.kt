package com.m3u.data.extension.emby

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.extension.api.subscription.EmbyCompatibleProviderKinds
import com.m3u.extension.api.subscription.SubscriptionProviderSettingKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmbyCompatibleProviderLocalizationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun simplifiedChineseDescriptorUsesLocalizedLabels() {
        val descriptor = EmbyCompatibleProvider.descriptorForLocale(context, "zh-Hans-CN")
        val variants = descriptor.variants.associate { it.kind to it.displayName }
        val fields = descriptor.settingsSchema!!.fields.associate { it.key to it.label }

        assertEquals("Emby / Jellyfin", descriptor.displayName)
        assertEquals("Emby", variants[EmbyCompatibleProviderKinds.Emby])
        assertEquals("Jellyfin", variants[EmbyCompatibleProviderKinds.Jellyfin])
        assertEquals("自动检测", variants[EmbyCompatibleProviderKinds.Auto])
        assertEquals(
            setOf(EmbyCompatibleProviderKinds.Emby, EmbyCompatibleProviderKinds.Jellyfin),
            descriptor.variants.filter { it.userSelectable }.mapTo(mutableSetOf()) { it.kind },
        )
        assertEquals(
            false,
            descriptor.variants.single { it.kind == EmbyCompatibleProviderKinds.Auto }.userSelectable,
        )
        assertEquals("订阅地址", fields[SubscriptionProviderSettingKeys.BaseUrl])
        assertEquals("用户名", fields[SubscriptionProviderSettingKeys.Username])
        assertEquals("密码", fields[SubscriptionProviderSettingKeys.Password])
    }

    @Test
    fun englishDescriptorUsesEnglishLabels() {
        val descriptor = EmbyCompatibleProvider.descriptorForLocale(context, "en-US")
        val variants = descriptor.variants.associate { it.kind to it.displayName }
        val fields = descriptor.settingsSchema!!.fields.associate { it.key to it.label }

        assertEquals("Emby / Jellyfin", descriptor.displayName)
        assertEquals("Emby", variants[EmbyCompatibleProviderKinds.Emby])
        assertEquals("Jellyfin", variants[EmbyCompatibleProviderKinds.Jellyfin])
        assertEquals("Automatic", variants[EmbyCompatibleProviderKinds.Auto])
        assertEquals("address", fields[SubscriptionProviderSettingKeys.BaseUrl])
        assertEquals("username", fields[SubscriptionProviderSettingKeys.Username])
        assertEquals("password", fields[SubscriptionProviderSettingKeys.Password])
    }

    @Test
    fun translatedProjectLocaleKeepsProviderFieldsLocalized() {
        val descriptor = EmbyCompatibleProvider.descriptorForLocale(context, "fr-FR")
        val fields = descriptor.settingsSchema!!.fields.associate { it.key to it.label }

        assertEquals("adresse", fields[SubscriptionProviderSettingKeys.BaseUrl])
        assertEquals("nom d'utilisateur", fields[SubscriptionProviderSettingKeys.Username])
        assertEquals("mot de passe", fields[SubscriptionProviderSettingKeys.Password])
    }

    @Test
    fun wireDisplayTextRemovesEveryControlRejectedByProviderDiscovery() {
        val bidiControls = buildList {
            add('\u061C')
            add('\u200E')
            add('\u200F')
            addAll(('\u202A'..'\u202E').toList())
            addAll(('\u2066'..'\u2069').toList())
        }.joinToString(separator = "")
        val sanitized = EmbyCompatibleProvider.wireSafeDisplayText(
            "$bidiControls server\u0000\naddress $bidiControls"
        )

        assertEquals("server address", sanitized)
        assertFalse(
            sanitized.any { character ->
                character.isISOControl() || character.isExtensionBidiControl()
            }
        )
    }

    private fun Char.isExtensionBidiControl(): Boolean =
        code == 0x061C ||
            code in 0x202A..0x202E ||
            code in 0x2066..0x2069 ||
            code == 0x200E ||
            code == 0x200F
}
