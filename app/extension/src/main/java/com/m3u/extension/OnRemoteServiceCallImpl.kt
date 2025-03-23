package com.m3u.extension

import android.util.Log
import com.google.auto.service.AutoService
import com.m3u.data.extension.IRemoteCallback
import com.m3u.data.extension.OnRemoteServerCall

@AutoService(OnRemoteServerCall::class)
class OnRemoteServiceCallImpl: OnRemoteServerCall {
    override fun onCall(module: String, method: String, param: String, callback: IRemoteCallback?) {
        Log.d(TAG, "onCall: $module, $method, $param, $callback")
    }
    companion object {
        private const val TAG = "OnRemoteServiceCallImpl"
    }
}