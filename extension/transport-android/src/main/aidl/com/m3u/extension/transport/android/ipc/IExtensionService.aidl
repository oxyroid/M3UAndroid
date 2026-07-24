package com.m3u.extension.transport.android.ipc;

import android.os.ParcelFileDescriptor;
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge;
import com.m3u.extension.transport.android.ipc.IExtensionResultCallback;

oneway interface IExtensionService {
    void handshake(
        @nullable String requestId,
        in @nullable ParcelFileDescriptor request,
        @nullable IExtensionResultCallback callback
    );
    void openManifest(
        @nullable String requestId,
        @nullable IExtensionResultCallback callback
    );
    void invoke(
        @nullable String requestId,
        @nullable String invocationId,
        @nullable String hookId,
        int schemaVersion,
        in @nullable ParcelFileDescriptor request,
        @nullable IExtensionHostBridge hostBridge,
        @nullable IExtensionResultCallback callback
    );
    void cancel(@nullable String invocationId);
    void health(
        @nullable String requestId,
        @nullable IExtensionResultCallback callback
    );
}
