@file:Suppress("UNCHECKED_CAST")
@file:OptIn(ExperimentalAtomicApi::class)

package com.m3u.core.foundation.architecture.preferences

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

typealias Settings = DataStore<Preferences>

private val settingsDataStore: ReadOnlyProperty<Context, Settings> =
    object : ReadOnlyProperty<Context, Settings> {
        private val property = preferencesDataStore("settings")
        private var instance: Settings? = null
        override fun getValue(
            thisRef: Context,
            property: KProperty<*>
        ): Settings = instance ?: this.property.getValue(thisRef, property).apply {
            runBlocking {
                applyDefaultValues()
            }
        }
    }
val Context.settings: Settings by settingsDataStore

@Composable
fun <T> preferenceOf(
    key: Preferences.Key<T>,
    initial: T = remember(key) { PREFERENCES[key] as T },
): State<T> {
    val dataStore: Settings = LocalContext.current.settings
    return produceState(initial, key1 = dataStore) {
        dataStore.data.mapNotNull { it[key] }.collect {
            value = it
        }
    }
}

@Composable
fun <T> mutablePreferenceOf(
    key: Preferences.Key<T>,
    initial: T = remember(key) { PREFERENCES[key] as T },
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): MutableState<T> {
    val state = preferenceOf(key, initial)
    val dataStore: Settings = LocalContext.current.settings
    return remember(key, initial) {
        object : MutableState<T> {
            override fun component1(): T = this.value
            override fun component2(): (T) -> Unit = { this.value = it }
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        dataStore.edit {
                            it[key] = value
                        }
                    }
                }
        }
    }
}

fun <T> Settings.flowOf(key: Preferences.Key<T>): Flow<T> = data.mapNotNull { it[key] }

suspend operator fun <T> Settings.get(key: Preferences.Key<T>): T = coroutineScope {
    data // cold flow
        .mapNotNull { it[key] }
        .first()
}

suspend operator fun <T> Settings.set(key: Preferences.Key<T>, value: T) {
    edit { it[key] = value }
}

private val PREFERENCES: Map<Preferences.Key<*>, Any> = buildMap {
    put(PreferencesKeys.PLAYLIST_STRATEGY, PlaylistStrategy.ALL)
    put(PreferencesKeys.ROW_COUNT, 1)
    put(PreferencesKeys.CONNECT_TIMEOUT, ConnectTimeout.SHORT)
    put(PreferencesKeys.GOD_MODE, false)
    put(PreferencesKeys.CLIP_MODE, ClipMode.ADAPTIVE)
    put(PreferencesKeys.AUTO_REFRESH_CHANNELS, false)
    put(PreferencesKeys.FULL_INFO_PLAYER, false)
    put(PreferencesKeys.NO_PICTURE_MODE, false)
    put(PreferencesKeys.DARK_MODE, true)
    put(PreferencesKeys.USE_DYNAMIC_COLORS, false)
    put(PreferencesKeys.FOLLOW_SYSTEM_THEME, false)
    put(PreferencesKeys.ZAPPING_MODE, false)
    put(PreferencesKeys.BRIGHTNESS_GESTURE, true)
    put(PreferencesKeys.VOLUME_GESTURE, true)
    put(PreferencesKeys.SCREENCAST, true)
    put(PreferencesKeys.SCREEN_ROTATING, false)
    put(PreferencesKeys.UNSEENS_MILLISECONDS, UnseensMilliseconds.DAYS_3)
    put(PreferencesKeys.RECONNECT_MODE, ReconnectMode.NO)
    put(PreferencesKeys.COLOR_ARGB, 0x5E6738)
    put(PreferencesKeys.TUNNELING, false)
    put(PreferencesKeys.CLOCK_MODE, false)
    put(PreferencesKeys.REMOTE_CONTROL, false)
    put(PreferencesKeys.SLIDER, true)
    put(PreferencesKeys.ALWAYS_SHOW_REPLAY, false)
    put(PreferencesKeys.PLAYER_PANEL, true)
    put(PreferencesKeys.COMPACT_DIMENSION, false)
    put(PreferencesKeys.EXTERNAL_EXTENSIONS, false)
}

suspend fun Settings.applyDefaultValues() {
    if (applied.compareAndSet(expectedValue = false, newValue = true)) {
        edit { pref ->
            PREFERENCES.forEach { (key, defaultValue) ->
                if (key !in pref) {
                    pref.set<Any>(key as Preferences.Key<Any>, defaultValue)
                }
            }
        }
    }
}

private val applied = AtomicBoolean(false)

object PreferencesKeys {
    val PLAYLIST_STRATEGY = intPreferencesKey("playlist-strategy")
    val ROW_COUNT = intPreferencesKey("rowCount")

    val CONNECT_TIMEOUT = longPreferencesKey("connect-timeout")
    val GOD_MODE = booleanPreferencesKey("god-mode")

    val CLIP_MODE = intPreferencesKey("clip-mode")
    val AUTO_REFRESH_CHANNELS = booleanPreferencesKey("auto-refresh-channels")
    val FULL_INFO_PLAYER = booleanPreferencesKey("full-info-player")
    val NO_PICTURE_MODE = booleanPreferencesKey("no-picture-mode")
    val DARK_MODE = booleanPreferencesKey("dark-mode")
    val USE_DYNAMIC_COLORS = booleanPreferencesKey("use-dynamic-colors")
    val FOLLOW_SYSTEM_THEME = booleanPreferencesKey("follow-system-theme")
    val ZAPPING_MODE = booleanPreferencesKey("zapping-mode")
    val BRIGHTNESS_GESTURE = booleanPreferencesKey("brightness-gesture")
    val VOLUME_GESTURE = booleanPreferencesKey("volume-gesture")
    val SCREENCAST = booleanPreferencesKey("screencast")
    val SCREEN_ROTATING = booleanPreferencesKey("screen-rotating")
    val UNSEENS_MILLISECONDS = longPreferencesKey("unseens-milliseconds")
    val RECONNECT_MODE = intPreferencesKey("reconnect-mode")
    val COLOR_ARGB = intPreferencesKey("color-argb")
    val TUNNELING = booleanPreferencesKey("tunneling")
    val CLOCK_MODE = booleanPreferencesKey("12h-clock-mode")
    val REMOTE_CONTROL = booleanPreferencesKey("remote-control")

    val SLIDER = booleanPreferencesKey("slider")
    val ALWAYS_SHOW_REPLAY = booleanPreferencesKey("always-show-replay")
    val PLAYER_PANEL = booleanPreferencesKey("player_panel")

    val COMPACT_DIMENSION = booleanPreferencesKey("compact-dimension")
    val EXTERNAL_EXTENSIONS = booleanPreferencesKey("external-extensions")
}
