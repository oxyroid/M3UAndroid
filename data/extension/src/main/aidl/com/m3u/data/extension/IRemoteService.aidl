package com.m3u.data.extension;

import com.m3u.data.extension.IRemoteCallback;

interface IRemoteService {
    void call(String func, String param, IRemoteCallback callback);
}