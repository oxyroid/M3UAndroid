package com.m3u.business.extension

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.extension.api.CallTokenConst
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import javax.inject.Inject

data class App(
    val name: String,
    val icon: Drawable,
    val packageName: String,
    val mainClassName: String,
    val version: String,
    val description: String,
)

@HiltViewModel
class ExtensionViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    val applications: StateFlow<List<App>> = flow {
        val pkgManager = context.packageManager
        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
        } else {
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }
        val extensions = installedPkgs
            .filter(::isPackageAnExtension)
            .mapNotNull { info ->
                val applicationInfo = info.applicationInfo ?: return@mapNotNull null
                val mainClass = applicationInfo.metaData?.getString(EXTENSION_MAIN_CLASS)
                    ?: return@mapNotNull null
                val name = pkgManager.getApplicationLabel(applicationInfo).toString()
                val icon = pkgManager.getApplicationIcon(applicationInfo)
                val version = applicationInfo.metaData?.getString(EXTENSION_VERSION).orEmpty()
                val description = applicationInfo.metaData?.getString(EXTENSION_DESCRIPTION).orEmpty()
                App(
                    name = name,
                    icon = icon,
                    packageName = info.packageName,
                    mainClassName = mainClass.let {
                        if (it.startsWith(".")) info.packageName + it else it
                    },
                    version = version,
                    description = description,
                )
            }
            .toList()
        emit(extensions)
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    fun runExtension(app: App) {
        val intent = Intent().apply {
            this.component = ComponentName(app.packageName, app.mainClassName)
            putExtra(CallTokenConst.PACKAGE_NAME, context.packageName)
            putExtra(CallTokenConst.CLASS_NAME, "com.m3u.extension.api.RemoteService")
            putExtra(
                CallTokenConst.PERMISSION,
                "${context.packageName}.permission.CONNECT_EXTENSION_PLUGIN"
            )
            putExtra(CallTokenConst.ACCESS_KEY, UUID.randomUUID().toString())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

}

@Suppress("DEPRECATION")
private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

private const val EXTENSION_FEATURE = "m3uandroid.extension"
private const val EXTENSION_MAIN_CLASS = "m3uandroid.extension.class"
private const val EXTENSION_VERSION = "m3uandroid.extension.version"
private const val EXTENSION_DESCRIPTION = "m3uandroid.extension.description"

private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
    return pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }
}