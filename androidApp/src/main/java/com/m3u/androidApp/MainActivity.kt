package com.m3u.androidApp

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.m3u.androidApp.ui.App
import com.m3u.androidApp.ui.AppViewModel
import com.m3u.ui.Events.enableDPadReaction
import com.m3u.ui.Toolkit
import com.m3u.ui.helper.Helper
import dagger.hilt.android.AndroidEntryPoint

import com.m3u.androidApp.ui.ExpiredPage
import android.annotation.SuppressLint
import android.provider.Settings
import com.m3u.androidApp.pocketbase.PocketBaseApi
import com.m3u.androidApp.pocketbase.models.DeviceCreateRequest
import kotlinx.coroutines.*
import retrofit2.awaitResponse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: AppViewModel by viewModels()

    private val helper: Helper = Helper(this)

    // Job to track delay coroutine
    private var delayJob: Job? = null

    override fun onResume() {
        super.onResume()
        helper.applyConfiguration()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        helper.applyConfiguration()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        enableDPadReaction()
        super.onCreate(savedInstanceState)

        setContent {
            Toolkit(helper) {
                LoadingScreen()
            }
        }

        // Start a coroutine to run checks and fetch data
        CoroutineScope(Dispatchers.Main).launch {
            // Fetch settings from backend
            val settings = fetchSettings()
            val days = settings?.days ?: 30 // Default to 30 days if not found
            val message = settings?.message ?: "Subscription expired"

            // Get Android ID
            val androidId = getAndroidId()

            // Fetch the device from backend
            val device = fetchDeviceByAndroidId(androidId)

            if (device != null) {
                // Check if device is expired
                val expiresAt = device.expiresAt
                if (isExpired(expiresAt)) {
                    navigateToExpiredPage(device.message, androidId)
                } else {
                    proceedWithNormalOperation()
                }
            } else {
                // Device not found, create a new one
                val expiresAt = LocalDateTime.now().plusDays(days.toLong()).format(DateTimeFormatter.ISO_DATE_TIME)
                val model = android.os.Build.MODEL

                val newDevice = createDevice(androidId, model, expiresAt, message)
                if (newDevice != null) {
                    proceedWithNormalOperation()
                } else {
                    // Handle error (you can add an error page or log this)
                }
            }
        }

        // Start the 5-second timeout and restart logic, assign to delayJob
        delayJob = CoroutineScope(Dispatchers.Main).launch {
            delay(5000) // Wait for 5 seconds
            if (isFinishing.not()) {
                restartApplication()  // Restart if no response within 5 seconds
            }
        }
    }

    // Get Android ID
    @SuppressLint("HardwareIds")
    private fun getAndroidId(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    // Check if the device is expired
    private fun isExpired(expiresAt: String): Boolean {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSX")
        return try {
            val expireDate = LocalDateTime.parse(expiresAt, formatter)
            val now = LocalDateTime.now()
            now.isAfter(expireDate)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Fetch settings
    private suspend fun fetchSettings() = withContext(Dispatchers.IO) {
        try {
            val response = PocketBaseApi.service.getSettings().awaitResponse()
            response.body()?.items?.get(0)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Fetch device by Android ID
    private suspend fun fetchDeviceByAndroidId(androidId: String) = withContext(Dispatchers.IO) {
        try {
            val filter = "android_id='$androidId'"
            val response = PocketBaseApi.service.getDeviceByAndroidId(filter).awaitResponse()
            response.body()?.items?.get(0)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Create a new device
    private suspend fun createDevice(androidId: String, model: String, expiresAt: String, message: String) = withContext(Dispatchers.IO) {
        val newDevice = DeviceCreateRequest(androidId, model, expiresAt, message)
        try {
            val response = PocketBaseApi.service.createDevice(newDevice).awaitResponse()
            response.body()?.items?.get(0)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Navigate to Expired Page
    private fun navigateToExpiredPage(message: String, androidId: String) {
        cancelDelay()  // Cancel the delay coroutine when navigating
        setContent {
            Toolkit(helper) {
                ExpiredPage(message = message, androidId = androidId)
            }
        }
    }

    // Proceed with normal operation
    private fun proceedWithNormalOperation() {
        cancelDelay()  // Cancel the delay coroutine when navigating
        setContent {
            Toolkit(helper) {
                App(viewModel = viewModel)
            }
        }
    }

    // Cancel the delay coroutine if it is running
    private fun cancelDelay() {
        delayJob?.cancel()
        delayJob = null
    }

    private fun restartApplication() {
        finish()
        startActivity(intent) // Restart the same activity
        overridePendingTransition(0, 0) // No transition animation
    }
}

// Loading screen composable
@Composable
fun LoadingScreen() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        CircularProgressIndicator()
    }
}
