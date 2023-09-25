package com.m3u.features.about

import android.app.Application
import com.m3u.core.architecture.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    application: Application
) : BaseViewModel<AboutState, AboutEvent>(
    application = application,
    emptyState = AboutState()
) {
    override fun onEvent(event: AboutEvent) {

    }
}