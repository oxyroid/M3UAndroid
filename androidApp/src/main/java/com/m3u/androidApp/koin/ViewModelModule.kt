package com.m3u.androidApp.koin

import com.m3u.androidApp.ui.RootViewModel
import com.m3u.features.console.ConsoleViewModel
import com.m3u.features.crash.screen.detail.DetailViewModel
import com.m3u.features.favorite.FavouriteViewModel
import com.m3u.features.feed.FeedViewModel
import com.m3u.features.live.LiveViewModel
import com.m3u.features.main.MainViewModel
import com.m3u.features.setting.SettingViewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val ViewModelModule = module {
    viewModelOf(::MainViewModel)
    viewModelOf(::FeedViewModel)
    viewModelOf(::LiveViewModel)
    viewModelOf(::FavouriteViewModel)
    viewModelOf(::SettingViewModel)
    viewModelOf(::ConsoleViewModel)
    viewModelOf(::RootViewModel)
    viewModelOf(::DetailViewModel)
}