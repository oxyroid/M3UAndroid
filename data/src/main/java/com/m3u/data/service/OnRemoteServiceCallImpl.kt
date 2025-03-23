package com.m3u.data.service

import android.util.Log
import com.google.auto.service.AutoService
import com.m3u.data.extension.IRemoteCallback
import com.m3u.data.extension.OnRemoteServerCall
import kotlin.random.Random

@AutoService(OnRemoteServerCall::class)
class OnRemoteServiceCallImpl : OnRemoteServerCall {
    override fun onCall(func: String, param: String, callback: IRemoteCallback?) {
        Log.d(TAG, "onCall: $func, $param, $callback")
        when (func) {
            "read-channel-count" -> {
                callback?.onSuccess(func, Random.nextInt().toString())
            }
        }
    }

    companion object {
        private const val TAG = "Host-OnRemoteServiceCallImpl"
    }
}