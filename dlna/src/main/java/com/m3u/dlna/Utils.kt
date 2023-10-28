package com.m3u.dlna

import android.Manifest.permission
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore.MediaColumns
import android.provider.MediaStore.Video
import java.io.File

object Utils {
    // ------------------------------------------------------------------------------------------------------------------------
    // ---- Device Wifi Information
    // ------------------------------------------------------------------------------------------------------------------------
    private const val UNKNOWN = "<unknown>"
    private const val WIFI_DISABLED = "<disabled>"
    private const val WIFI_NO_CONNECT = "<not connect>"
    private const val PERMISSION_DENIED = "<permission denied>"

    fun getWiFiIpAddress(context: Context): String {
        var ipAddress = 0
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            ipAddress = wifiManager.connectionInfo.ipAddress
        } else {
            val connectivityManager =
                context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.run {
                activeNetwork?.let { network ->
                    (getNetworkCapabilities(network)?.transportInfo as? WifiInfo)?.let { wifiInfo ->
                        ipAddress = wifiInfo.ipAddress
                    }
                }
            }
        }
        if (ipAddress == 0) return ""
        return (ipAddress and 0xFF).toString() + "." + (ipAddress shr 8 and 0xFF) + "." + (ipAddress shr 16 and 0xFF) + "." + (ipAddress shr 24 and 0xFF)
    }

    /**
     * need permission 'Manifest.permission.ACCESS_FINE_LOCATION' and 'Manifest.permission.ACCESS_WIFI_STATE' if system sdk >= Android O.
     */
    fun getWiFiName(context: Context): String {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) return WIFI_DISABLED
        val wifiInfo = wifiManager.connectionInfo ?: return WIFI_NO_CONNECT
        return if (wifiInfo.ssid == WifiManager.UNKNOWN_SSID) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (context.checkSelfPermission(permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    if (wifiManager.configuredNetworks != null) {
                        for (config in wifiManager.configuredNetworks) {
                            if (config.networkId == wifiInfo.networkId) {
                                return config.SSID.replace("\"".toRegex(), "")
                            }
                        }
                    }
                } else {
                    PERMISSION_DENIED
                }
            } else {
                return WIFI_NO_CONNECT
            }
            UNKNOWN
        } else {
            wifiInfo.ssid.replace("\"".toRegex(), "")
        }
    }

    fun getHttpBaseUrl(context: Context, port: Int = 9091) =
        "http://${getWiFiIpAddress(context)}:$port/"

    // ------------------------------------------------------------------------------------------------------------------------
    // ---- Others
    // ------------------------------------------------------------------------------------------------------------------------
    fun parseUri2File(context: Context, uri: Uri): File? {
        return if (DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val type = split[0]
            val path = if ("primary".equals(type, ignoreCase = true)) {
                Environment.getExternalStorageDirectory().toString() + "/" + split[1]
            } else if ("raw".equals(type, ignoreCase = true)) {
                split[1]
            } else {
                getDataColumn(
                    context,
                    Video.Media.EXTERNAL_CONTENT_URI,
                    "${MediaColumns._ID}=?",
                    arrayOf(split[1])
                )
            }
            path?.let { File(it) }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            getDataColumn(context, uri)?.let { File(it) }
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            uri.path?.let { File(it) }
        } else {
            null
        }
    }

    private fun getDataColumn(
        context: Context,
        uri: Uri,
        selection: String? = null,
        selectionArgs: Array<String>? = null
    ): String? {
        val projection = arrayOf(MediaColumns.DATA)
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(projection[0])
                return cursor.getString(columnIndex)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                cursor?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }
}
