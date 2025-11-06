package com.m3u.data.repository.usbkey

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.Settings
import com.m3u.core.architecture.preferences.get
import com.m3u.core.architecture.preferences.set
import com.m3u.data.database.DatabaseMigrationHelper
import com.m3u.data.logging.LogSanitizer
import com.m3u.data.security.EncryptionLockManager
import com.m3u.data.security.EncryptionMetricsCalculator
import com.m3u.data.security.KeyVerificationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

private const val ACTION_USB_PERMISSION = "com.m3u.USB_PERMISSION"
private const val USB_KEY_FILE_NAME = ".m3u_enc_key"

@Singleton
internal class USBKeyRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: Settings,
    private val migrationHelper: DatabaseMigrationHelper,
    private val keyVerificationManager: KeyVerificationManager,
    private val lockManager: EncryptionLockManager,
    private val logSanitizer: LogSanitizer,
    private val metricsCalculator: EncryptionMetricsCalculator
) : USBKeyRepository {

    private val timber = Timber.tag("USBKeyRepository")
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(USBKeyState())
    override val state: StateFlow<USBKeyState> = _state.asStateFlow()

    private val _progress = MutableStateFlow<EncryptionProgress?>(null)

    private var usbReceiver: BroadcastReceiver? = null

    init {
        registerUSBReceiver()
        checkUSBConnection()

        // Update metrics periodically in background
        repositoryScope.launch {
            updateStateMetrics()
        }
    }

    /**
     * Update state with current metrics
     */
    private suspend fun updateStateMetrics() {
        try {
            val autoLockEnabled = lockManager.isAutoLockEnabled()
            val databaseSize = metricsCalculator.calculateDatabaseSize()
            val encryptionAlgorithm = metricsCalculator.getEncryptionAlgorithm()
            val healthStatus = metricsCalculator.calculateHealthStatus(_state.value)

            _state.value = _state.value.copy(
                autoLockEnabled = autoLockEnabled,
                databaseSize = databaseSize,
                encryptionAlgorithm = encryptionAlgorithm,
                healthStatus = healthStatus
            )
        } catch (e: Exception) {
            timber.e(e, "Failed to update state metrics")
        }
    }

    private fun registerUSBReceiver() {
        usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        timber.d("USB device attached")
                        checkUSBConnection()
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        timber.d("USB device detached")

                        // Handle USB detachment in background to avoid blocking broadcast receiver
                        repositoryScope.launch {
                            val isEncryptionEnabled = _state.value.isEncryptionEnabled
                            val autoLockEnabled = lockManager.isAutoLockEnabled()

                            if (isEncryptionEnabled && autoLockEnabled) {
                                timber.d("Auto-lock enabled - locking application")
                                lockManager.lockApplication("USB_REMOVED")
                                _state.value = _state.value.copy(
                                    isConnected = false,
                                    deviceName = null,
                                    isDatabaseUnlocked = false,
                                    isLocked = true,
                                    lockReason = "USB device removed"
                                )
                            } else {
                                _state.value = _state.value.copy(
                                    isConnected = false,
                                    deviceName = null,
                                    isDatabaseUnlocked = false
                                )
                            }
                        }
                    }
                    ACTION_USB_PERMISSION -> {
                        synchronized(this) {
                            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                            }
                            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                                device?.let {
                                    timber.d("Permission granted for device ${it.deviceName}")
                                    checkUSBConnection()
                                }
                            } else {
                                timber.w("Permission denied for device ${device?.deviceName}")
                                _state.value = _state.value.copy(error = "USB permission denied")
                            }
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }

        ContextCompat.registerReceiver(
            context,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun checkUSBConnection() {
        timber.d("=== Checking USB Connection ===")

        // Method 1: Try StorageManager to check for removable storage
        var isConnected = false
        var deviceName: String? = null

        try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? android.os.storage.StorageManager
            if (storageManager != null) {
                val getVolumeListMethod = storageManager.javaClass.getMethod("getVolumeList")
                val volumes = getVolumeListMethod.invoke(storageManager) as? Array<*>

                timber.d("StorageManager found ${volumes?.size ?: 0} volumes")

                volumes?.forEach { volume ->
                    try {
                        val getPathMethod = volume!!.javaClass.getMethod("getPath")
                        val isRemovableMethod = volume.javaClass.getMethod("isRemovable")
                        val getStateMethod = volume.javaClass.getMethod("getState")

                        val path = getPathMethod.invoke(volume) as? String
                        val isRemovable = isRemovableMethod.invoke(volume) as? Boolean ?: false
                        val state = getStateMethod.invoke(volume) as? String

                        if (path != null && isRemovable && state == "mounted") {
                            timber.d("✓ Found mounted removable storage: $path")
                            isConnected = true
                            deviceName = path
                        }
                    } catch (e: Exception) {
                        timber.w(e, "Failed to inspect volume")
                    }
                }
            }
        } catch (e: Exception) {
            timber.w(e, "StorageManager check failed")
        }

        // Method 2: Fallback to UsbManager check
        if (!isConnected) {
            timber.d("Falling back to UsbManager check...")
            val deviceList = usbManager.deviceList
            val massStorageDevice = deviceList.values.firstOrNull { device ->
                // Check for mass storage device (class 8)
                device.interfaceCount > 0 && device.getInterface(0).interfaceClass == 8
            }

            if (massStorageDevice != null) {
                timber.d("✓ Found USB mass storage device via UsbManager: ${massStorageDevice.deviceName}")
                isConnected = true
                deviceName = massStorageDevice.deviceName
            }
        }

        // Method 3: Check if getUSBMountPoint() finds anything
        if (!isConnected) {
            timber.d("Falling back to mount point check...")
            val mountPoint = getUSBMountPoint()
            if (mountPoint != null) {
                timber.d("✓ Found USB mount point: ${mountPoint.absolutePath}")
                isConnected = true
                deviceName = mountPoint.absolutePath
            }
        }

        timber.d("Final USB connection state: connected=$isConnected, device=$deviceName")

        _state.value = _state.value.copy(
            isConnected = isConnected,
            deviceName = deviceName,
            error = if (!isConnected) "No USB device detected" else null
        )
    }

    override suspend fun requestUSBPermission(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val deviceList = usbManager.deviceList
            val device = deviceList.values.firstOrNull { device ->
                device.interfaceCount > 0 && device.getInterface(0).interfaceClass == 8
            }

            if (device == null) {
                return@withContext Result.failure(Exception("No USB device found"))
            }

            if (usbManager.hasPermission(device)) {
                return@withContext Result.success(Unit)
            }

            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
            )
            usbManager.requestPermission(device, permissionIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            timber.e(e, "Failed to request USB permission")
            Result.failure(e)
        }
    }

    override suspend fun initializeEncryption(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            timber.d("=== initializeEncryption() STARTED ===")

            // Export diagnostic logs to USB first
            exportLogsToUSB()

            // Check if USB is connected
            if (!_state.value.isConnected) {
                timber.e("USB device not connected")
                return@withContext Result.failure(Exception("No USB device connected"))
            }
            timber.d("USB device is connected: ${_state.value.deviceName}")

            // Generate 256-bit encryption key
            val key = ByteArray(32)
            SecureRandom().nextBytes(key)
            timber.d("Generated 256-bit encryption key")

            // Generate and store key fingerprint
            val fingerprint = keyVerificationManager.generateFingerprint(key)
            timber.d("Generated key fingerprint: ${fingerprint.takeLast(8)}")

            // Get USB mount point
            val usbMountPoint = getUSBMountPoint()
            if (usbMountPoint == null) {
                return@withContext Result.failure(Exception("Could not access USB storage"))
            }

            // Write key file to USB
            val keyFile = File(usbMountPoint, USB_KEY_FILE_NAME)
            keyFile.writeBytes(key)
            timber.d("Encryption key written to USB: ${keyFile.absolutePath}")

            // Mark file as hidden on compatible systems
            if (!keyFile.setReadable(true, true)) {
                timber.w("Could not set file permissions")
            }

            // Store key fingerprint
            keyVerificationManager.storeFingerprint(fingerprint)
            timber.d("Key fingerprint stored")

            // Store USB device serial/identifier for verification
            val device = getCurrentUSBDevice()
            if (device != null) {
                settings[PreferencesKeys.USB_ENCRYPTION_DEVICE_ID] = device.serialNumber ?: device.deviceName
                timber.d("Stored USB device ID")
            }

            // Set the "encryption pending" flag - this will be checked on next app startup
            settings[PreferencesKeys.USB_ENCRYPTION_IN_PROGRESS] = true
            settings[PreferencesKeys.USB_ENCRYPTION_LAST_OPERATION] = "ENCRYPTION_PENDING"
            timber.d("Set ENCRYPTION_PENDING flag")

            timber.d("=== Preparation complete. App will now restart to perform encryption with database closed ===")

            // CRITICAL: Kill the app process so the database gets closed
            // When Android restarts the app, the Application class will check the flag
            // and perform the encryption with NO database connections open
            withContext(Dispatchers.Main) {
                timber.d("Killing app process in 500ms...")
                kotlinx.coroutines.delay(500) // Give time for UI to show message
                android.os.Process.killProcess(android.os.Process.myPid())
            }

            Result.success(Unit)
        } catch (e: Exception) {
            timber.e(e, "Failed to initialize USB encryption")
            clearProgress()
            settings[PreferencesKeys.USB_ENCRYPTION_IN_PROGRESS] = false
            _state.value = _state.value.copy(error = e.message)
            Result.failure(e)
        }
    }

    override suspend fun performPendingEncryption(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            timber.d("=== performPendingEncryption() STARTED ===")
            timber.d("Database is now CLOSED - ready to perform encryption!")

            // Read the encryption key from USB
            val key = getEncryptionKey()
            if (key == null) {
                timber.e("Encryption key not found on USB")
                return@withContext Result.failure(Exception("Encryption key not found"))
            }
            timber.d("Encryption key loaded from USB")

            // Check if database exists and needs migration
            val isDatabaseEncrypted = migrationHelper.isDatabaseEncrypted()
            timber.d("Database encryption status: $isDatabaseEncrypted")

            if (isDatabaseEncrypted == false) {
                timber.d("Database exists and is unencrypted - starting migration NOW")

                // CRITICAL: Database is CLOSED - we can now safely encrypt it!
                val migrationResult = migrationHelper.migrateToEncrypted(key) { progress ->
                    timber.d("Migration progress: $progress%")
                }

                if (migrationResult.isFailure) {
                    timber.e("Database migration failed: ${migrationResult.exceptionOrNull()?.message}")
                    return@withContext Result.failure(
                        Exception("Database migration failed: ${migrationResult.exceptionOrNull()?.message}")
                    )
                }
                timber.d("✓ Database encrypted successfully!")

                // Cleanup backups after successful encryption
                timber.d("Cleaning up backup files...")
                migrationHelper.cleanupBackups()
                timber.d("✓ Backups cleaned up")
            } else if (isDatabaseEncrypted == true) {
                timber.w("Database is already encrypted")
            } else {
                timber.d("No existing database found")
            }

            timber.d("=== performPendingEncryption() COMPLETED SUCCESSFULLY ===")
            Result.success(Unit)
        } catch (e: Exception) {
            timber.e(e, "Failed to perform pending encryption")
            Result.failure(e)
        }
    }

    private fun updateProgress(step: EncryptionStep, percentage: Int, operation: String) {
        timber.d("Progress: $step - $percentage% - $operation")
        _progress.value = EncryptionProgress(step, percentage, null, operation)
        _state.value = _state.value.copy(encryptionProgress = _progress.value)
    }

    private fun clearProgress() {
        _progress.value = null
        _state.value = _state.value.copy(encryptionProgress = null)
    }

    private fun restartApp() {
        try {
            timber.d("=== RESTARTING APP ===")
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)

            // Kill the current process to force full restart
            android.os.Process.killProcess(android.os.Process.myPid())
        } catch (e: Exception) {
            timber.e(e, "Failed to restart app")
        }
    }

    override suspend fun disableEncryption(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Mark decryption operation as in progress
            settings[PreferencesKeys.USB_ENCRYPTION_IN_PROGRESS] = true
            settings[PreferencesKeys.USB_ENCRYPTION_LAST_OPERATION] = "DECRYPTION"

            updateProgress(EncryptionStep.PREPARING, 5, "Preparing decryption")

            // Get current encryption key before deleting it
            val key = getEncryptionKey()

            // Check if database is encrypted and needs decryption
            updateProgress(EncryptionStep.VERIFYING, 10, "Checking database status")
            val isDatabaseEncrypted = migrationHelper.isDatabaseEncrypted()
            timber.d("Database encryption status before disable: $isDatabaseEncrypted")

            if (isDatabaseEncrypted == true && key != null) {
                // Database is encrypted - decrypt it
                updateProgress(EncryptionStep.MIGRATING_DATA, 20, "Decrypting database")
                timber.d("Starting database decryption migration")

                val migrationResult = migrationHelper.migrateToUnencrypted(key) { progress ->
                    // Map migration progress (0-100) to our overall progress (20-85)
                    val overallProgress = 20 + (progress * 0.65).toInt()
                    updateProgress(EncryptionStep.MIGRATING_DATA, overallProgress, "Decrypting database: $progress%")
                }

                if (migrationResult.isFailure) {
                    clearProgress()
                    settings[PreferencesKeys.USB_ENCRYPTION_IN_PROGRESS] = false
                    return@withContext Result.failure(
                        Exception("Database decryption failed: ${migrationResult.exceptionOrNull()?.message}")
                    )
                }
                timber.d("Database decrypted successfully")
            } else if (isDatabaseEncrypted == false) {
                // Database is already unencrypted
                timber.d("Database is already unencrypted, skipping migration")
                updateProgress(EncryptionStep.MIGRATING_DATA, 85, "Database already unencrypted")
            } else {
                // No database exists
                timber.d("No existing database found")
                updateProgress(EncryptionStep.MIGRATING_DATA, 85, "No database to decrypt")
            }

            // Delete key file from USB if available
            updateProgress(EncryptionStep.FINALIZING, 90, "Removing encryption key")
            val usbMountPoint = getUSBMountPoint()
            if (usbMountPoint != null) {
                val keyFile = File(usbMountPoint, USB_KEY_FILE_NAME)
                if (keyFile.exists()) {
                    keyFile.delete()
                    timber.d("Deleted encryption key file from USB")
                }
            }

            // Clear key fingerprint
            keyVerificationManager.clearFingerprint()

            // Clear encryption settings
            settings[PreferencesKeys.USB_ENCRYPTION_ENABLED] = false
            settings[PreferencesKeys.USB_ENCRYPTION_DEVICE_ID] = ""

            updateProgress(EncryptionStep.FINALIZING, 95, "Cleaning up")

            _state.value = _state.value.copy(
                isEncryptionEnabled = false,
                isDatabaseUnlocked = false,
                keyVerified = false,
                lastVerificationTime = null
            )

            // Clean up backup files after successful decryption
            migrationHelper.cleanupBackups()

            timber.d("USB encryption disabled successfully")

            updateProgress(EncryptionStep.COMPLETE, 100, "Decryption complete")

            // Restart app to reinitialize database without encryption
            timber.d("Restarting app to apply decryption...")
            restartApp()

            Result.success(Unit)
        } catch (e: Exception) {
            timber.e(e, "Failed to disable USB encryption")
            clearProgress()
            settings[PreferencesKeys.USB_ENCRYPTION_IN_PROGRESS] = false
            Result.failure(e)
        }
    }

    override suspend fun validateUSBKey(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!isEncryptionEnabled()) {
                return@withContext Result.success(false)
            }

            if (!_state.value.isConnected) {
                return@withContext Result.success(false)
            }

            // Verify it's the correct USB device
            val device = getCurrentUSBDevice()
            val storedDeviceId = settings[PreferencesKeys.USB_ENCRYPTION_DEVICE_ID] ?: ""
            val currentDeviceId = device?.serialNumber ?: device?.deviceName ?: ""

            if (storedDeviceId.isNotEmpty() && currentDeviceId != storedDeviceId) {
                timber.w("USB device ID mismatch. Expected: $storedDeviceId, Got: $currentDeviceId")
                return@withContext Result.success(false)
            }

            // Check if key file exists
            val key = getEncryptionKey()
            val isValid = key != null && key.size == 32

            // Verify fingerprint if key is valid
            if (isValid && key != null) {
                validateKeyFingerprint(key)
            }

            _state.value = _state.value.copy(isDatabaseUnlocked = isValid)

            Result.success(isValid)
        } catch (e: Exception) {
            timber.e(e, "Failed to validate USB key")
            Result.failure(e)
        }
    }

    /**
     * Validate that the current USB key matches the stored fingerprint
     */
    private suspend fun validateKeyFingerprint(key: ByteArray) {
        try {
            timber.d("Validating key fingerprint...")
            val verified = keyVerificationManager.verifyKey(key)

            if (verified) {
                timber.d("Key fingerprint validation successful")
                _state.value = _state.value.copy(
                    keyVerified = true,
                    lastVerificationTime = System.currentTimeMillis(),
                    verificationError = null
                )
            } else {
                timber.w("Key fingerprint validation failed - mismatch detected")
                _state.value = _state.value.copy(
                    keyVerified = false,
                    verificationError = "Key fingerprint mismatch"
                )
            }
        } catch (e: Exception) {
            timber.e(e, "Error during key fingerprint validation")
            _state.value = _state.value.copy(
                keyVerified = false,
                verificationError = "Verification error: ${e.message}"
            )
        }
    }

    override suspend fun getEncryptionKey(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val usbMountPoint = getUSBMountPoint() ?: return@withContext null
            val keyFile = File(usbMountPoint, USB_KEY_FILE_NAME)

            if (!keyFile.exists()) {
                timber.w("Key file not found on USB")
                return@withContext null
            }

            val key = keyFile.readBytes()
            if (key.size != 32) {
                timber.e("Invalid key size: ${key.size} bytes")
                return@withContext null
            }

            timber.d("Successfully read encryption key from USB")
            key
        } catch (e: Exception) {
            timber.e(e, "Failed to read encryption key from USB")
            null
        }
    }

    override fun isEncryptionEnabled(): Boolean {
        return runBlocking {
            settings[PreferencesKeys.USB_ENCRYPTION_ENABLED] ?: false
        }
    }

    private fun getCurrentUSBDevice(): UsbDevice? {
        val deviceList = usbManager.deviceList
        return deviceList.values.firstOrNull { device ->
            device.interfaceCount > 0 && device.getInterface(0).interfaceClass == 8
        }
    }

    private suspend fun exportLogsToUSB() {
        try {
            timber.d("=== EXPORTING LOGS TO USB ===")

            // Get USB mount point
            var usbMountPoint = getUSBMountPoint()
            if (usbMountPoint == null) {
                timber.w("Cannot export logs - no writable USB mount point found")
                return
            }

            timber.d("USB mount point for logs: ${usbMountPoint.absolutePath}")

            // Try to write to root of USB first, not subdirectories
            // Navigate up to the actual USB root if we're in a subdirectory
            while (usbMountPoint != null &&
                   (usbMountPoint.absolutePath.contains("/Android/data") ||
                    usbMountPoint.absolutePath.contains("/files"))) {
                usbMountPoint = usbMountPoint.parentFile
                timber.d("Navigating up to: ${usbMountPoint?.absolutePath}")
            }

            if (usbMountPoint == null || !usbMountPoint.canWrite()) {
                timber.e("USB root is not writable")
                return
            }

            timber.d("Final USB root for logs: ${usbMountPoint.absolutePath}")

            // Capture logcat output
            val timestamp = System.currentTimeMillis()
            val logFileName = "m3u_diagnostics_$timestamp.txt"
            val logFile = File(usbMountPoint, logFileName)

            timber.d("Log file path: ${logFile.absolutePath}")

            // Execute logcat command to capture logs
            val process = Runtime.getRuntime().exec(
                arrayOf(
                    "logcat",
                    "-d",  // Dump existing logs
                    "-v", "time",  // Include timestamps
                    "USBKeyRepository:D",
                    "WebServerRepository:D",
                    "SecuritySection:D",
                    "SettingViewModel:D",
                    "*:S"  // Silence all other logs
                )
            )

            // Read the output
            var logContent = process.inputStream.bufferedReader().use { it.readText() }

            // Check if sanitization is enabled
            val sanitizationEnabled = settings[PreferencesKeys.DIAGNOSTIC_LOG_SANITIZATION_ENABLED] ?: true
            if (sanitizationEnabled) {
                timber.d("Log sanitization enabled - sanitizing logs...")
                logContent = logSanitizer.sanitize(logContent)
                timber.d("Log sanitization complete: ${logSanitizer.getSanitizationSummary()}")
            } else {
                timber.w("Log sanitization disabled - exporting raw logs")
            }

            // Add diagnostic header
            val diagnosticReport = buildString {
                appendLine("=".repeat(70))
                appendLine("M3U ANDROID - DIAGNOSTIC LOG EXPORT")
                appendLine("=".repeat(70))
                appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(timestamp))}")
                appendLine()
                appendLine("DEVICE HARDWARE INFORMATION:")
                appendLine("-".repeat(70))
                appendLine("Device Manufacturer: ${android.os.Build.MANUFACTURER}")
                appendLine("Device Brand: ${android.os.Build.BRAND}")
                appendLine("Device Model: ${android.os.Build.MODEL}")
                appendLine("Device Product: ${android.os.Build.PRODUCT}")
                appendLine("Device Hardware: ${android.os.Build.HARDWARE}")
                appendLine("Device Board: ${android.os.Build.BOARD}")
                appendLine("Device Type: ${android.os.Build.TYPE}")
                appendLine("Build Fingerprint: ${android.os.Build.FINGERPRINT}")
                appendLine("Android Version: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
                appendLine()
                appendLine("USB ENCRYPTION STATUS:")
                appendLine("-".repeat(70))
                appendLine("USB Mount Point: ${usbMountPoint.absolutePath}")
                appendLine("USB Device Connected: ${_state.value.isConnected}")
                appendLine("USB Device Name: ${_state.value.deviceName ?: "N/A"}")
                appendLine("Encryption Enabled: ${_state.value.isEncryptionEnabled}")
                appendLine("Database Unlocked: ${_state.value.isDatabaseUnlocked}")
                appendLine("=".repeat(70))
                appendLine()
                appendLine("CAPTURED LOGS:")
                appendLine("=".repeat(70))
                appendLine(logContent)
            }

            // Write to file
            logFile.writeText(diagnosticReport)

            timber.d("✓ Logs exported successfully to: ${logFile.absolutePath}")
            timber.d("✓ Log file size: ${logFile.length()} bytes")

        } catch (e: Exception) {
            timber.e(e, "Failed to export logs to USB")
            timber.e("Error type: ${e.javaClass.simpleName}")
            timber.e("Error message: ${e.message}")
        }
    }

    private fun getUSBMountPoint(): File? {
        timber.d("=== Starting USB mount point search ===")

        // Method 1: Try StorageManager API (best for Android TV)
        try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? android.os.storage.StorageManager
            if (storageManager != null) {
                timber.d("Using StorageManager to find USB storage...")

                // Use reflection to access getVolumeList which is not in public API
                val getVolumeListMethod = storageManager.javaClass.getMethod("getVolumeList")
                val volumes = getVolumeListMethod.invoke(storageManager) as? Array<*>

                timber.d("Found ${volumes?.size ?: 0} storage volumes")

                volumes?.forEachIndexed { index, volume ->
                    try {
                        val getPathMethod = volume!!.javaClass.getMethod("getPath")
                        val isRemovableMethod = volume.javaClass.getMethod("isRemovable")
                        val getStateMethod = volume.javaClass.getMethod("getState")

                        val path = getPathMethod.invoke(volume) as? String
                        val isRemovable = isRemovableMethod.invoke(volume) as? Boolean ?: false
                        val state = getStateMethod.invoke(volume) as? String

                        timber.d("Volume $index: path=$path, removable=$isRemovable, state=$state")

                        if (path != null && isRemovable && state == "mounted") {
                            val volumeDir = File(path)
                            timber.d("Checking removable volume: ${volumeDir.absolutePath}")
                            timber.d("  exists: ${volumeDir.exists()}, canRead: ${volumeDir.canRead()}, canWrite: ${volumeDir.canWrite()}")

                            if (volumeDir.exists() && volumeDir.canRead()) {
                                // Try to write directly to USB root
                                if (volumeDir.canWrite()) {
                                    timber.d("✓ Found writable USB root: ${volumeDir.absolutePath}")
                                    return volumeDir
                                }

                                // Try app-specific directory on USB
                                val appDir = File(volumeDir, "Android/data/${context.packageName}/files")
                                timber.d("Trying app-specific directory: ${appDir.absolutePath}")
                                if (!appDir.exists()) {
                                    val created = appDir.mkdirs()
                                    timber.d("Created app directory: $created")
                                }
                                if (appDir.exists() && appDir.canWrite()) {
                                    timber.d("✓ Found writable app-specific USB directory: ${appDir.absolutePath}")
                                    return appDir
                                }
                            }
                        }
                    } catch (e: Exception) {
                        timber.w(e, "Failed to inspect volume $index")
                    }
                }
            }
        } catch (e: Exception) {
            timber.w(e, "StorageManager method failed, trying fallback")
        }

        // Method 2: Try common USB mount points
        timber.d("Trying common USB mount points...")
        val possibleMountPoints = listOf(
            "/storage/usbotg",
            "/storage/usb",
            "/mnt/usb",
            "/mnt/usbstorage",
            "/mnt/media_rw",
            "/storage"
        )

        for (mountPoint in possibleMountPoints) {
            timber.d("Checking mount point: $mountPoint")
            val dir = File(mountPoint)
            timber.d("  exists: ${dir.exists()}, isDirectory: ${dir.isDirectory}")

            if (dir.exists() && dir.isDirectory) {
                // Check subdirectories
                val subdirs = dir.listFiles()
                timber.d("  subdirectories: ${subdirs?.size ?: 0}")

                subdirs?.forEach { subDir ->
                    timber.d("    checking ${subDir.absolutePath}: isDir=${subDir.isDirectory}, canRead=${subDir.canRead()}, canWrite=${subDir.canWrite()}")

                    if (subDir.isDirectory && subDir.name != "emulated") {
                        // Try writable first
                        if (subDir.canWrite()) {
                            timber.d("✓ Found writable USB mount point: ${subDir.absolutePath}")
                            return subDir
                        }

                        // If not writable, try app-specific directory
                        val appDir = File(subDir, "Android/data/${context.packageName}/files")
                        if (!appDir.exists()) {
                            val created = appDir.mkdirs()
                            timber.d("Created app-specific directory: $created")
                        }
                        if (appDir.exists() && appDir.canWrite()) {
                            timber.d("✓ Found writable app-specific USB directory: ${appDir.absolutePath}")
                            return appDir
                        }
                    }
                }
            }
        }

        // Method 3: Use getExternalFilesDirs (secondary external storage is usually USB)
        timber.d("Trying external storage directories via getExternalFilesDirs...")
        context.getExternalFilesDirs(null)?.forEachIndexed { index, dir ->
            timber.d("External dir $index: ${dir?.absolutePath}")
            if (dir != null && index > 0) { // index > 0 means not primary storage
                timber.d("  exists: ${dir.exists()}, canWrite: ${dir.canWrite()}")

                if (dir.exists() && dir.canWrite()) {
                    // Go up to the actual USB root, not the app-specific directory
                    var usbRoot: File? = dir
                    while (usbRoot != null && usbRoot.absolutePath.contains("/Android/data")) {
                        usbRoot = usbRoot.parentFile?.parentFile?.parentFile?.parentFile
                    }

                    if (usbRoot != null && usbRoot.exists()) {
                        timber.d("Found USB root from external dir: ${usbRoot.absolutePath}")
                        if (usbRoot.canWrite()) {
                            timber.d("✓ Using USB root: ${usbRoot.absolutePath}")
                            return usbRoot
                        } else {
                            // Return the app-specific directory if root is not writable
                            timber.d("✓ Using app-specific directory on USB: ${dir.absolutePath}")
                            return dir
                        }
                    }
                }
            }
        }

        // Method 4: Last resort - app's primary external storage (for testing)
        val appExternalDir = context.getExternalFilesDir(null)
        if (appExternalDir != null && appExternalDir.canWrite()) {
            timber.w("⚠ Using app's external storage as last resort fallback: ${appExternalDir.absolutePath}")
            timber.w("⚠ This is NOT a USB drive - encryption will still work but key won't be on USB!")
            return appExternalDir
        }

        timber.e("✗ Could not find any writable USB mount point")
        timber.e("✗ USB encryption cannot proceed without writable storage")
        return null
    }
}
