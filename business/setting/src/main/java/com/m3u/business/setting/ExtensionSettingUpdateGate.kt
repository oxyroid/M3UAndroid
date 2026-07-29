package com.m3u.business.setting

internal class ExtensionSettingUpdateGate {
    private val activeKeys = mutableMapOf<String, MutableSet<String>>()

    fun tryStart(
        extensionId: String,
        qualifiedKey: String,
    ): Boolean = synchronized(activeKeys) {
        activeKeys
            .getOrPut(extensionId, ::mutableSetOf)
            .add(qualifiedKey)
    }

    fun finish(
        extensionId: String,
        qualifiedKey: String,
    ) {
        synchronized(activeKeys) {
            activeKeys[extensionId]?.let { keys ->
                keys.remove(qualifiedKey)
                if (keys.isEmpty()) activeKeys.remove(extensionId)
            }
        }
    }

    fun clear(extensionId: String) {
        synchronized(activeKeys) {
            activeKeys.remove(extensionId)
        }
    }
}
