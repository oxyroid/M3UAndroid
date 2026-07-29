package com.m3u.extension.transport.android.ipc;

import android.os.ParcelFileDescriptor;

oneway interface IExtensionResultCallback {
    void onSuccess(
        @nullable String requestId,
        in @nullable ParcelFileDescriptor response
    );
    void onFailure(
        @nullable String requestId,
        @nullable String code,
        @nullable String message
    );
}
