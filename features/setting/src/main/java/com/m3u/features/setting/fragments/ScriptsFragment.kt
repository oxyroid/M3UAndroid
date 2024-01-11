package com.m3u.features.setting.fragments

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.m3u.material.components.Button
import com.m3u.material.components.OuterColumn
import com.m3u.i18n.R.string

@Composable
internal fun ScriptsFragment(
    contentPadding: PaddingValues,
    importJavaScript: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { url ->
        url ?: return@rememberLauncherForActivityResult
        importJavaScript(url)
    }
    OuterColumn(
        modifier = modifier
    ) {
        LazyColumn(
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {

        }
        Button(
            text = stringResource(string.feat_setting_script_management_import_js),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                launcher.launch(arrayOf("text/javascript"))
            }
        )
    }
}