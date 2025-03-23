package com.m3u.data.extension;

interface IRemoteCallback {
    void onSuccess(String module, String method, String param);
    void onError(String module, String method, int errorCode, String errorMessage);
}