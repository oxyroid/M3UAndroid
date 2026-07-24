package com.m3u.data.extension.emby

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.extension.api.subscription.EmbyCompatibleProviderKinds
import com.m3u.extension.api.subscription.SubscriptionProviderSettingKeys
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmbyCompatibleProviderLocalizationTest {
    @Test
    fun simplifiedChineseDescriptorUsesLocalizedLabels() {
        val descriptor = EmbyCompatibleProvider.descriptorForLocale("zh-Hans-CN")
        val variants = descriptor.variants.associate { it.kind to it.displayName }
        val fields = descriptor.settingsSchema!!.fields.associate { it.key to it.label }

        assertEquals("Emby / Jellyfin 服务器", descriptor.displayName)
        assertEquals("Emby", variants[EmbyCompatibleProviderKinds.Emby])
        assertEquals("Jellyfin", variants[EmbyCompatibleProviderKinds.Jellyfin])
        assertEquals("自动检测", variants[EmbyCompatibleProviderKinds.Auto])
        assertEquals("服务器地址", fields[SubscriptionProviderSettingKeys.BaseUrl])
        assertEquals("用户名", fields[SubscriptionProviderSettingKeys.Username])
        assertEquals("密码", fields[SubscriptionProviderSettingKeys.Password])
    }

    @Test
    fun englishDescriptorUsesEnglishLabels() {
        val descriptor = EmbyCompatibleProvider.descriptorForLocale("en-US")
        val variants = descriptor.variants.associate { it.kind to it.displayName }
        val fields = descriptor.settingsSchema!!.fields.associate { it.key to it.label }

        assertEquals("Emby / Jellyfin server", descriptor.displayName)
        assertEquals("Automatic", variants[EmbyCompatibleProviderKinds.Auto])
        assertEquals("Server URL", fields[SubscriptionProviderSettingKeys.BaseUrl])
        assertEquals("Username", fields[SubscriptionProviderSettingKeys.Username])
        assertEquals("Password", fields[SubscriptionProviderSettingKeys.Password])
    }

    @Test
    fun unsupportedTraditionalChineseFallsBackToEnglish() {
        val descriptor = EmbyCompatibleProvider.descriptorForLocale("zh-Hant-TW")

        assertEquals("Emby / Jellyfin server", descriptor.displayName)
        assertEquals(
            "Automatic",
            descriptor.variants.single { it.kind == EmbyCompatibleProviderKinds.Auto }.displayName,
        )
    }
}
