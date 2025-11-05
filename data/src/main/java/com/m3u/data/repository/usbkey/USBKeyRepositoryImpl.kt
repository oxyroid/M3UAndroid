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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val migrationHelper: DatabaseMigrationHelper
) : USBKeyRepository {

    private val timber = Timber.tag("USBKeyRepository")
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _state = MutableStateFlow(USBKeyState())
    override val state: StateFlow<USBKeyState> = _state.asStateFlow()

    private var usbReceiver: BroadcastReceiver? = null

    init {
        registerUSBReceiver()
        checkUSBConnection()
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
                        _state.value = _state.value.copy(
                            isConnected = false,
                            deviceName = null,
                            isDatabaseUnlocked = false
                        )
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
        val deviceList = usbManager.deviceList
        val massStorageDevice = deviceList.values.firstOrNull { device ->
            // Check for mass storage device (class 8)
            device.interfaceCount > 0 && device.getInterface(0).interfaceClass == 8
        }

        if (massStorageDevice != null) {
            timber.d("Found USB mass storage device: ${massStorageDevice.deviceName}")
            _state.value = _state.value.copy(
                isConnected = true,
                deviceName = massStorageDevice.deviceName,
                error = null
            )
        } else {
            _state.value = _state.value.copy(
                isConnected = false,
                deviceName = null
            )
        }
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
            // Check if USB is connected
            if (!_state.value.isConnected) {
                return@withContext Result.failure(Exception("No USB device connected"))
            }

            // Generate 256-bit encryption key
            val key = ByteArray(32)
            SecureRandom().nextBytes(key)

            // Get USB mount point
            val usbMountPoint = getUSBMountPoint()
            if (usbMountPoint == null) {
                return@withContext Result.failure(Exception("Could not access USB storage"))
            }

            // Write key file to USB
            val keyFile = File(usbMountPoint, USB_KEY_FILE_NAME)
            keyFile.writeBytes(key)

            // Mark file as hidden on compatible systems
            if (!keyFile.setReadable(true, true)) {
                timber.w("Could not set file permissions")
            }

            // Check if database exists and needs migration
            val isDatabaseEncrypted = migrationHelper.isDatabaseEncrypted()
            timber.d("Database encryption status: $isDatabaseEncrypted")

            if (isDatabaseEncrypted == false) {
                // Database exists and is unencrypted - migrate it
                timber.d("Starting database encryption migration")
                val migrationResult = migrationHelper.migrateToEncrypted(key)

                if (migrationResult.isFailure) {
                    // Clean up key file if migration failed
                    keyFile.delete()
                    return@withContext Result.failure(
                        Exception("Database migration failed: ${migrationResult.exceptionOrNull()?.message}")
                    )
                }
                timber.d("Database encrypted successfully")
            } else if (isDatabaseEncrypted == true) {
                // Database is already encrypted - this shouldn't happen
                timber.w("Database is already encrypted, skipping migration")
            } else {
                // Database doesn't exist yet - no migration needed
                timber.d("No existing database found, will create encrypted database on first use")
            }

            // Store USB device serial/identifier
            val device = getCurrentUSBDevice()
            if (device != null) {
                settings[PreferencesKeys.USB_ENCRYPTION_DEVICE_ID] = device.serialNumber ?: device.deviceName
            }

            // Enable encryption flag
            settings[PreferencesKeys.USB_ENCRYPTION_ENABLED] = true

            _state.value = _state.value.copy(
                isEncryptionEnabled = true,
                isDatabaseUnlocked = true
            )

            // Clean up backup files after successful setup
            migrationHelper.cleanupBackups()

            timber.d("USB encryption initialized successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            timber.e(e, "Failed to initialize USB encryption")
            _state.value = _state.value.copy(error = e.message)
            Result.failure(e)
        }
    }

    override suspend fun disableEncryption(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Get current encryption key before deleting it
            val key = getEncryptionKey()

            // Check if database is encrypted and needs decryption
            val isDatabaseEncrypted = migrationHelper.isDatabaseEncrypted()
            timber.d("Database encryption status before disable: $isDatabaseEncrypted")

            if (isDatabaseEncrypted == true && key != null) {
                // Database is encrypted - decrypt it
                timber.d("Starting database decryption migration")
                val migrationResult = migrationHelper.migrateToUnencrypted(key)

                if (migrationResult.isFailure) {
                    return@withContext Result.failure(
                        Exception("Database decryption failed: ${migrationResult.exceptionOrNull()?.message}")
                    )
                }
                timber.d("Database decrypted successfully")
            } else if (isDatabaseEncrypted == false) {
                // Database is already unencrypted
                timber.d("Database is already unencrypted, skipping migration")
            } else {
                // No database exists
                timber.d("No existing database found")
            }

            // Delete key file from USB if available
            val usbMountPoint = getUSBMountPoint()
            if (usbMountPoint != null) {
                val keyFile = File(usbMountPoint, USB_KEY_FILE_NAME)
                if (keyFile.exists()) {
                    keyFile.delete()
                    timber.d("Deleted encryption key file from USB")
                }
            }

            // Clear encryption settings
            settings[PreferencesKeys.USB_ENCRYPTION_ENABLED] = false
            settings[PreferencesKeys.USB_ENCRYPTION_DEVICE_ID] = ""

            _state.value = _state.value.copy(
                isEncryptionEnabled = false,
                isDatabaseUnlocked = false
            )

            // Clean up backup files after successful decryption
            migrationHelper.cleanupBackups()

            timber.d("USB encryption disabled successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            timber.e(e, "Failed to disable USB encryption")
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

            _state.value = _state.value.copy(isDatabaseUnlocked = isValid)

            Result.success(isValid)
        } catch (e: Exception) {
            timber.e(e, "Failed to validate USB key")
            Result.failure(e)
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

    private fun getUSBMountPoint(): File? {
        // Try to find USB mount point
        val possibleMountPoints = listOf(
            "/storage/usbotg",
            "/storage/usb",
            "/mnt/usb",
            "/mnt/media_rw",
            "/storage"
        )

        for (mountPoint in possibleMountPoints) {
            val dir = File(mountPoint)
            if (dir.exists() && dir.isDirectory) {
                // Check subdirectories
                dir.listFiles()?.forEach { subDir ->
                    if (subDir.isDirectory && subDir.canWrite()) {
                        timber.d("Found writable USB mount point: ${subDir.absolutePath}")
                        return subDir
                    }
                }
            }
        }

        // Fallback: try external storage directories
        context.getExternalFilesDirs(null)?.forEach { dir ->
            if (dir != null && dir.absolutePath.contains("usb", ignoreCase = true)) {
                timber.d("Found USB storage via external files: ${dir.absolutePath}")
                return dir
            }
        }

        timber.w("Could not find USB mount point")
        return null
    }
}
