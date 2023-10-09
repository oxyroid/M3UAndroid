package com.m3u.core.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toFile

fun Uri.readContentFilename(
    contentResolver: ContentResolver
): String? = when (scheme) {
    ContentResolver.SCHEME_FILE -> toFile().name
    ContentResolver.SCHEME_CONTENT -> {
        contentResolver.query(
            this,
            null,
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(
                    OpenableColumns.DISPLAY_NAME
                )
                cursor.getString(index)
            } else null
        }
    }

    else -> null
}

fun Uri.readContentText(
    contentResolver: ContentResolver
): String? {
    return when (scheme) {
        ContentResolver.SCHEME_FILE -> toFile().readText()
        ContentResolver.SCHEME_CONTENT ->
            contentResolver.openInputStream(this)?.use {
                it.bufferedReader().readText()
            }

        else -> null
    }
}