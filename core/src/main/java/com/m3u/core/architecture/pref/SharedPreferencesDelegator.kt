package com.m3u.core.architecture.pref

import android.content.SharedPreferences

internal class SharedPreferencesDelegator(
    private val delegate: SharedPreferences,
    private val strategy: Int = STRATEGY_NORMAL
) : SharedPreferences {
    companion object {
        const val STRATEGY_NORMAL = 0
        const val STRATEGY_CACHE = 1
    }

    private val cache = mutableMapOf<String, Any>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> get(
        key: String,
        originGetter: () -> T
    ): T {
        return when (strategy) {
            STRATEGY_CACHE -> (cache[key] as? T) ?: originGetter().also {
                it?.let { cache[key] = it }
            }

            else -> originGetter()
        }
    }

    override fun getAll(): MutableMap<String, *> {
        return delegate.all
    }

    override fun getString(key: String, defValue: String?): String? {
        return get(key) {
            delegate.getString(key, defValue)
        }
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        return get(key) {
            delegate.getStringSet(key, defValues)
        }
    }

    override fun getInt(key: String, defValue: Int): Int {
        return get(key) {
            delegate.getInt(key, defValue)
        }
    }

    override fun getLong(key: String, defValue: Long): Long {
        return get(key) {
            delegate.getLong(key, defValue)
        }
    }

    override fun getFloat(key: String, defValue: Float): Float {
        return get(key) {
            delegate.getFloat(key, defValue)
        }
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return get(key) {
            delegate.getBoolean(key, defValue)
        }
    }

    override fun contains(key: String): Boolean {
        return delegate.contains(key)
    }

    override fun edit(): SharedPreferences.Editor {
        return delegate.edit()
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        delegate.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        delegate.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
