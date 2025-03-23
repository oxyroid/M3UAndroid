package com.m3u.data.extension;

interface IRemoteCallback {
    void onSuccess(String func, String param);
    void onError(String func, String errorCode, String errorMessage);
}