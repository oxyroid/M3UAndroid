package com.m3u.extension.transport.android.ipc;

import android.os.ParcelFileDescriptor;

interface IExtensionHostBridge {
    ParcelFileDescriptor executeHttp(in ParcelFileDescriptor request);
}
