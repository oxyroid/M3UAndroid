package com.m3u.material.ktx

import android.util.Log

fun log(tag: String = "", body: Any = "") {
    Log.e(tag.ifEmpty { Thread.currentThread().name }.uppercase(), body.toString().ifEmpty { "log" })
}