package com.m3u.features.setting.parts

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.m3u.features.setting.R
import com.m3u.ui.components.Button
import com.m3u.ui.components.OuterColumn

@Composable
internal fun ScriptsPart(
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
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {

        }
        Button(
            textRes = R.string.script_management_import_js,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                launcher.launch(arrayOf("text/javascript"))
            }
        )
    }
}