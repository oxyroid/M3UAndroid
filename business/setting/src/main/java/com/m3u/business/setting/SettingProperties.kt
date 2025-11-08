package com.m3u.business.setting

import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.m3u.data.database.model.DataSource

class SettingProperties(
    val titleState: MutableState<String> = mutableStateOf(""),
    val urlState: MutableState<String> = mutableStateOf(""),
    val uriState: MutableState<Uri> = mutableStateOf(Uri.EMPTY),
    val localStorageState: MutableState<Boolean> = mutableStateOf(false),
    val basicUrlState: MutableState<String> = mutableStateOf(""),
    val usernameState: MutableState<String> = mutableStateOf(""),
    val passwordState: MutableState<String> = mutableStateOf(""),
    val epgState: MutableState<String> = mutableStateOf(""),
    val selectedState: MutableState<DataSource> = mutableStateOf(DataSource.WebDrop)
)
