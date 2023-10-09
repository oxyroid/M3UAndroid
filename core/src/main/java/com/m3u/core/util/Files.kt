package com.m3u.core.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toFile

fun Uri.readContentFilename(context: Context): String? = when (scheme) {
    ContentResolver.SCHEME_FILE -> toFile().name
    ContentResolver.SCHEME_CONTENT -> {
        context.contentResolver.query(
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

fun Uri.readContentText(context: Context): String? {
    return when (scheme) {
        ContentResolver.SCHEME_FILE -> toFile().readText()
        ContentResolver.SCHEME_CONTENT ->
            context.contentResolver.openInputStream(this)?.use {
                it.bufferedReader().readText()
            }

        else -> null
    }
}