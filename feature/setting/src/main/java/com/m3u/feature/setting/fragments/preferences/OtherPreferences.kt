package com.m3u.feature.setting.fragments.preferences

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PermDeviceInformation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.m3u.core.unit.DataUnit
import com.m3u.core.util.basic.title
import com.m3u.i18n.R.string
import com.m3u.material.components.Preference
import com.m3u.material.components.TrailingIconPreference
import com.m3u.material.model.LocalSpacing

@Composable
internal fun OtherPreferences(
    versionName: String,
    versionCode: Int,
    cacheSpace: DataUnit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        modifier = modifier
    ) {
        TrailingIconPreference(
            title = stringResource(string.feat_setting_system_setting).title(),
            icon = Icons.Rounded.PermDeviceInformation,
            trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
            onClick = {
                val intent =
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                context.startActivity(intent)
            }
        )
        Preference(
            title = stringResource(string.feat_setting_clear_cache).title(),
            content = cacheSpace.toString(),
            icon = Icons.Rounded.Delete,
            onClick = onClearCache
        )
        Preference(
            title = stringResource(string.feat_setting_app_version).title(),
            content = "$versionName ($versionCode)",
            icon = Icons.Rounded.Info,
        )
        Preference(
            title = stringResource(string.feat_setting_source_code).title(),
            content = "@oxyroid/M3UAndroid",
            icon = Icons.Rounded.Book,
            onClick = {
                uriHandler.openUri("https://github.com/oxyroid/M3UAndroid")
            }
        )
    }
}