package com.m3u.feature.extension

import androidx.lifecycle.ViewModel
import com.m3u.extension.runtime.ExtensionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ExtensionViewModel @Inject constructor(
    private val extensionManager: ExtensionManager
): ViewModel() {
    internal val extensions = extensionManager.extensions
    init {
        extensionManager.refreshExtensionPackages()
    }
}