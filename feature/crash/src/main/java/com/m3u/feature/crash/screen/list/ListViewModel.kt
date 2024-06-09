package com.m3u.feature.crash.screen.list

import androidx.lifecycle.ViewModel
import com.m3u.core.architecture.FileProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ListViewModel @Inject constructor(
    fileProvider: FileProvider
) : ViewModel() {
    internal val files: List<File> = fileProvider.readAll()
}
