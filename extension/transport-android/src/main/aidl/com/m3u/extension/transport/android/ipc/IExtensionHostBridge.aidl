package com.m3u.extension.transport.android.ipc;

import android.os.ParcelFileDescriptor;
import com.m3u.extension.transport.android.ipc.IExtensionResultCallback;

oneway interface IExtensionHostBridge {
    void executeHttp(
        @nullable String requestId,
        in @nullable ParcelFileDescriptor request,
        @nullable IExtensionResultCallback callback
    );
    void cancelHttp(@nullable String requestId);
}
