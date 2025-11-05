package com.m3u.smartphone.ui.business.setting.fragments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.business.setting.SettingViewModel
import com.m3u.smartphone.ui.business.setting.components.USBEncryptionContent
import com.m3u.smartphone.ui.material.ktx.plus
import com.m3u.smartphone.ui.material.model.LocalSpacing

@Composable
internal fun SecurityFragment(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val viewModel: SettingViewModel = hiltViewModel()
    val usbKeyState by viewModel.usbKeyState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        item {
            with(viewModel.properties) {
                USBEncryptionContent(
                    usbKeyState = usbKeyState,
                    onEnableEncryption = { viewModel.enableUSBEncryption() },
                    onDisableEncryption = { viewModel.disableUSBEncryption() },
                    onRequestUSBPermission = { viewModel.requestUSBPermission() }
                )
            }
        }
    }
}
