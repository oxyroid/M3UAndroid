@file:SuppressLint("UseHelperIssue")

package com.m3u.androidApp

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.m3u.androidApp.ui.App
import com.m3u.androidApp.ui.AppViewModel
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.Main
import com.m3u.core.architecture.pref.Pref
import com.m3u.data.service.PlayerManager
import com.m3u.data.service.RemoteDirectionService
import com.m3u.ui.Toolkit
import com.m3u.ui.helper.AbstractHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: AppViewModel by viewModels()
    private val helper by lazy {
        AbstractHelper(
            activity = this,
            mainDispatcher = mainDispatcher,
            playerManager = playerManager,
            title = viewModel.title,
            message = viewModel.message,
            actions = viewModel.actions,
            fob = viewModel.fob
        )
    }

    @Inject
    lateinit var pref: Pref

    @Inject
    lateinit var playerManager: PlayerManager

    @Inject
    @Dispatcher(Main)
    lateinit var mainDispatcher: CoroutineDispatcher

    @Inject
    lateinit var remoteDirectionService: RemoteDirectionService

    override fun onResume() {
        super.onResume()
        helper.applyConfiguration()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        helper.onUserLeaveHint?.invoke()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        helper.applyConfiguration()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            Toolkit(
                helper = helper,
                pref = pref,
                actions = remoteDirectionService.actions
            ) {
                App(
                    viewModel = viewModel
                )
            }
        }
    }
}
