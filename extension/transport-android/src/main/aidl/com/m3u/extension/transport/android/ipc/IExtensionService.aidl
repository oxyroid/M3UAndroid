package com.m3u.extension.transport.android.ipc;

import android.os.ParcelFileDescriptor;
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge;

interface IExtensionService {
    ParcelFileDescriptor openManifest();
    ParcelFileDescriptor invoke(
        String invocationId,
        String hookId,
        int schemaVersion,
        in ParcelFileDescriptor request,
        in IExtensionHostBridge hostBridge
    );
    void cancel(String invocationId);
    String health();
}
