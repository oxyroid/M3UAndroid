package com.m3u.features.setting.fragments.preferences

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PermDeviceInformation
import androidx.compose.material.icons.rounded.Source
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.m3u.core.util.basic.title
import com.m3u.i18n.R
import com.m3u.material.components.IconPreference
import com.m3u.material.components.Preference

@Composable
internal fun OtherPreferences(
    versionName: String,
    versionCode: Int,
    snapshot: Boolean,
    navigateToAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier) {
        IconPreference(
            title = stringResource(R.string.feat_setting_system_setting).title(),
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            icon = Icons.Rounded.PermDeviceInformation,
            onClick = {
                val intent =
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                context.startActivity(intent)
            }
        )
        // TODO: https://www.dropbox.com/developers/documentation/http/documentation#file_requests-list
        Preference(
            title = stringResource(R.string.feat_setting_dropbox).uppercase(),
            icon = Icons.Rounded.Backup,
            onClick = navigateToAbout,
            enabled = false
        )
        Preference(
            title = stringResource(R.string.feat_setting_project_about).title(),
            icon = Icons.Rounded.Source,
            onClick = navigateToAbout
        )
        Preference(
            title = stringResource(R.string.feat_setting_app_version).title(),
            content = "$versionName ($versionCode)" + if (snapshot) " SNAPSHOT" else "",
            icon = Icons.Rounded.Info,
        )
    }
}