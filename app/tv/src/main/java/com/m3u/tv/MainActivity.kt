package com.m3u.tv

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.m3u.business.setting.UnlockManager
import com.m3u.tv.screens.common.ErrorScreen
import com.m3u.tv.screens.common.LoadingScreen
import com.m3u.tv.screens.security.PINUnlockScreen
import com.m3u.tv.utils.Helper
import com.m3u.tv.utils.LocalHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var unlockManager: UnlockManager

    private val helper = Helper(this)

    private val timber = Timber.tag("MainActivity")

    // Storage permission request launcher
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        timber.d("Storage permissions result: $permissions")
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            timber.d("✓ All storage permissions granted")
        } else {
            timber.w("⚠ Some storage permissions denied: ${permissions.filter { !it.value }}")
        }
    }

    // Manage all files permission launcher for Android 11+
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                timber.d("✓ All files access granted")
            } else {
                timber.w("⚠ All files access denied")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        timber.d("=== MAIN ACTIVITY ONCREATE ===")

        // Request storage permissions on first launch
        requestStoragePermissions()

        // Initialize unlock manager BEFORE setContent
        // This checks if PIN encryption is enabled and sets initial lock state
        lifecycleScope.launch {
            timber.d("Initializing unlock manager...")
            unlockManager.initialize()
            timber.d("Unlock manager initialized")
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    // ========================================
                    // AUTHENTICATION GATE
                    // ========================================
                    // Observe the lock state and show different screens based on it
                    val lockState by unlockManager.lockState.collectAsStateWithLifecycle()

                    timber.d("Current lock state: $lockState")

                    when (lockState) {
                        is UnlockManager.LockState.Initializing -> {
                            // Show loading while checking encryption status
                            timber.d("Showing loading screen")
                            LoadingScreen(message = "Initializing...")
                        }

                        is UnlockManager.LockState.Locked -> {
                            // Database is encrypted - show PIN unlock screen
                            // This BLOCKS access to the main app
                            timber.d("Showing PIN unlock screen")

                            var errorMessage by remember { mutableStateOf<String?>(null) }

                            PINUnlockScreen(
                                onPINEntered = { pin ->
                                    timber.d("PIN entered in unlock screen, attempting unlock...")
                                    lifecycleScope.launch {
                                        val result = unlockManager.attemptUnlock(pin)
                                        if (result.isFailure) {
                                            timber.w("Unlock failed: ${result.exceptionOrNull()?.message}")
                                            errorMessage = "Incorrect PIN. Please try again."
                                        } else {
                                            timber.d("✓ Unlock successful!")
                                            errorMessage = null
                                            // State will automatically change to Unlocked
                                        }
                                    }
                                },
                                errorMessage = errorMessage
                            )
                        }

                        is UnlockManager.LockState.NoEncryption,
                        is UnlockManager.LockState.Unlocked -> {
                            // No encryption OR successfully unlocked - proceed to main app
                            timber.d("Proceeding to main app (unlocked or no encryption)")

                            CompositionLocalProvider(
                                LocalHelper provides helper,
                                LocalContentColor provides MaterialTheme.colorScheme.onBackground
                            ) {
                                App {
                                    onBackPressedDispatcher.onBackPressed()
                                }
                            }
                        }

                        is UnlockManager.LockState.Error -> {
                            // Error during initialization
                            val error = lockState as UnlockManager.LockState.Error
                            timber.e("Error state: ${error.message}")

                            ErrorScreen(
                                message = error.message,
                                onRetry = {
                                    lifecycleScope.launch {
                                        unlockManager.initialize()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        timber.d("=== MAIN ACTIVITY SETUP COMPLETE ===")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Handle DELETE and PAGE_DOWN as back navigation
        return when (keyCode) {
            KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                timber.d("Back navigation triggered by key: $keyCode")
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun requestStoragePermissions() {
        timber.d("=== STORAGE PERMISSION CHECK ===")
        timber.d("Android SDK Version: ${Build.VERSION.SDK_INT}")

        when {
            // Android 11+ (API 30+) requires special "All files access" permission
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                timber.d("Android 11+ detected - checking All Files Access")
                if (!Environment.isExternalStorageManager()) {
                    timber.d("All Files Access not granted - attempting to open settings")
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        manageStorageLauncher.launch(intent)
                        timber.d("✓ Launched settings for All Files Access")
                    } catch (e: Exception) {
                        timber.w(e, "Unable to request All Files Access on this device (TV doesn't support this)")
                        // On Android TV, this permission doesn't exist - just skip it
                        // The app can still access its own cache/data directories without this permission
                    }
                } else {
                    timber.d("✓ All Files Access already granted")
                }
            }
            // Android 6-10 (API 23-29) requires runtime permissions
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                timber.d("Android 6-10 detected - checking storage permissions")
                val permissionsToRequest = mutableListOf<String>()

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    timber.d("WRITE_EXTERNAL_STORAGE not granted")
                }

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    timber.d("READ_EXTERNAL_STORAGE not granted")
                }

                if (permissionsToRequest.isNotEmpty()) {
                    timber.d("Requesting ${permissionsToRequest.size} storage permissions")
                    storagePermissionLauncher.launch(permissionsToRequest.toTypedArray())
                } else {
                    timber.d("✓ All storage permissions already granted")
                }
            }
            // Android 5 and below (API <23) - permissions granted at install time
            else -> {
                timber.d("Android 5 or below - permissions granted at install time")
            }
        }
    }
}
