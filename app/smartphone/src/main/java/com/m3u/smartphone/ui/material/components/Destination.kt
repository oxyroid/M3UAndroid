package com.m3u.smartphone.ui.material.components

import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.m3u.i18n.R.string
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Immutable
enum class Destination(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @param:StringRes @field:StringRes val iconTextId: Int
) {
    Foryou(
        selectedIcon = Icons.Rounded.Home,
        unselectedIcon = Icons.Outlined.Home,
        iconTextId = string.ui_destination_foryou
    ),
    Favorite(
        selectedIcon = Icons.Rounded.Collections,
        unselectedIcon = Icons.Outlined.Collections,
        iconTextId = string.ui_destination_favourite
    ),
    Setting(
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        iconTextId = string.ui_destination_setting
    );

    companion object {
        fun of(route: String?): Destination? = try {
            route?.let { valueOf(it) }
        } catch (ignored: Exception) {
            null
        }
    }
}

@Immutable
@Parcelize
sealed interface SettingDestination : Parcelable {
    @Immutable
    @Parcelize
    data object Default : SettingDestination

    @Immutable
    @Parcelize
    data object Playlists : SettingDestination

    @Immutable
    @Parcelize
    data class PlaylistConfiguration(
        val playlistReference: String,
    ) : SettingDestination

    @Immutable
    @Parcelize
    data object PlaylistSourcePicker : SettingDestination

    @Immutable
    @Parcelize
    data class PlaylistEditor(
        val sourceKey: String,
        val draftKey: String = UUID.randomUUID().toString(),
        val providerId: String? = null,
        val providerKind: String? = null,
        val reauthenticationPlaylistUrl: String? = null,
    ) : SettingDestination

    @Immutable
    @Parcelize
    data object PlaylistEpgSources : SettingDestination

    @Immutable
    @Parcelize
    data object PlaylistHiddenChannels : SettingDestination

    @Immutable
    @Parcelize
    data object PlaylistHiddenCategories : SettingDestination

    @Immutable
    @Parcelize
    data object ExtensionPlugins : SettingDestination

    @Immutable
    @Parcelize
    data class ExtensionPluginDetails(
        val packageName: String,
        val serviceName: String,
    ) : SettingDestination

    @Immutable
    @Parcelize
    data class ExtensionPluginAuthorization(
        val packageName: String,
        val serviceName: String,
        val reauthorize: Boolean,
    ) : SettingDestination

    @Immutable
    @Parcelize
    data class ExtensionPluginSettings(
        val extensionId: String,
    ) : SettingDestination

    @Immutable
    @Parcelize
    data object Appearance : SettingDestination

    @Immutable
    @Parcelize
    data object Optional : SettingDestination

    @Immutable
    @Parcelize
    data object CodecPack : SettingDestination
}
