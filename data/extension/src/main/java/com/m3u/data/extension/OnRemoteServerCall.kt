package com.m3u.data.extension

interface OnRemoteServerCall {
    fun onCall(func: String, param: String, callback: IRemoteCallback?)
}
