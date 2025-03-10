package com.m3u.tv.screens.profile

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun AboutSection() {
    val context = LocalContext.current
    val versionNumber = remember(context) {
        context.getVersionNumber()
    }

    Column(modifier = Modifier.padding(horizontal = 72.dp)) {
        Text(
            text = "AboutSectionTitle",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            modifier = Modifier
                .graphicsLayer { alpha = 0.8f }
                .padding(top = 16.dp),
            text = "AboutSectionDescription",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.border.copy(alpha = 0.6f))
        )
        Text(
            modifier = Modifier
                .graphicsLayer { alpha = 0.6f }
                .padding(top = 16.dp),
            text = "AboutSectionAppVersionTitle",
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = versionNumber,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

private fun Context.getVersionNumber(): String {
    val packageName = packageName
    val metaData = packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
    return metaData.versionName!!
}
