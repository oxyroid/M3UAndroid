package com.m3u.core.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toFile

fun Uri.readFileName(resolver: ContentResolver): String? {
    return if (this == Uri.EMPTY) null
    else if (scheme == ContentResolver.SCHEME_FILE) toFile().name
    else if (scheme != ContentResolver.SCHEME_CONTENT) null
    else {
        val cursor = resolver.query(this, null, null, null, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                cursor.getString(index)
            } else null
        }
    }

}

fun Uri.readFileContent(resolver: ContentResolver): String? {
    return if (this == Uri.EMPTY) null
    else when (scheme) {
        ContentResolver.SCHEME_FILE -> toFile().readText()
        ContentResolver.SCHEME_CONTENT ->
            resolver.openInputStream(this)?.use {
                it.bufferedReader().readText()
            }

        else -> null
    }
}