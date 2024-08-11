package com.m3u.material.ktx

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.m3u.material.model.LocalSpacing
import com.m3u.material.model.asTvScheme
import com.m3u.material.model.asTvTypography
import androidx.tv.material3.MaterialTheme as TvMaterialTheme

@Composable
fun tv(): Boolean = LocalConfiguration.current.run {
    val type = uiMode and Configuration.UI_MODE_TYPE_MASK
    type == Configuration.UI_MODE_TYPE_TELEVISION
}

/**
 * Check current Platform and apply new colorScheme.
 * @param fallback apply std material3 MaterialTheme as well.
 */
@Composable
internal fun PlatformTheme(
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    typography: Typography = MaterialTheme.typography,
    fallback: Boolean = true,
    block: @Composable () -> Unit
) {
    val tv = tv()
    val car = false
    val content = @Composable {
        when {
            tv -> {
                TvMaterialTheme(
                    colorScheme = remember(colorScheme) { colorScheme.asTvScheme() },
                    typography = remember(typography) { typography.asTvTypography() }
                ) {
                    block()
                }
            }

            car -> throw UnsupportedOperationException()
            else -> block()
        }
    }
    val commonPlatform = !tv && !car
    if (commonPlatform || fallback) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
fun Modifier.includeChildGlowPadding(): Modifier = thenIf(tv()) {
    Modifier.padding(LocalSpacing.current.medium)
}
