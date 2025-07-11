package com.m3u.smartphone

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.extension.Utils
import com.m3u.i18n.R.string
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.acra.config.mailSender
import org.acra.config.notification
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltAndroidApp
class M3UApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    @Logger.MessageImpl
    lateinit var messager: Logger

    override fun onCreate() {
        super.onCreate()
        Utils.init(this)
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        initAcra {
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON
            notification {
                title = getString(string.crash_notification_title)
                text = getString(string.crash_notification_text)
                channelName = getString(string.crash_notification_channel_name)
            }
            mailSender {
                mailTo = "oxyroid@outlook.com"
                reportAsFile = true
                reportFileName = "Crash.txt"
            }
        }
        MainScope().launch {
            delay(3.seconds)
            throw RuntimeException("Test crash to check ACRA integration")
        }
    }

    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
}
