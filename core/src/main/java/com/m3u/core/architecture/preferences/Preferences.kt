@file:Suppress("INVISIBLE_REFERENCE", "UNCHECKED_CAST")
@file:OptIn(ExperimentalAtomicApi::class)

package com.m3u.core.architecture.preferences

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
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
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import java.util.UUID

typealias Settings = DataStore<Preferences>

private val settingsDataStore: ReadOnlyProperty<Context, Settings> = object : ReadOnlyProperty<Context, Settings> {
    private val property = preferencesDataStore("settings")
    private var instance: Settings? = null
    override fun getValue(
        thisRef: Context,
        ignored: KProperty<*>
    ): Settings = instance ?: property.getValue(thisRef, ignored).apply {
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

    return object : MutableState<T> {
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
    } as MutableState<T>
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

private val PREFERENCES: Map<Preferences.Key<*>, *> = listOf(
    PreferencesKeys.DEVICE_ID to "",
    PreferencesKeys.PLAYLIST_STRATEGY to PlaylistStrategy.ALL,
    PreferencesKeys.ROW_COUNT to 1,
    PreferencesKeys.DEVICE_ID to UUID.randomUUID().toString(),
    PreferencesKeys.CONNECT_TIMEOUT to ConnectTimeout.SHORT,
    PreferencesKeys.GOD_MODE to false,
    PreferencesKeys.CLIP_MODE to ClipMode.ADAPTIVE,
    PreferencesKeys.AUTO_REFRESH_CHANNELS to false,
    PreferencesKeys.FULL_INFO_PLAYER to false,
    PreferencesKeys.NO_PICTURE_MODE to false,
    PreferencesKeys.DARK_MODE to true,
    PreferencesKeys.USE_DYNAMIC_COLORS to false,
    PreferencesKeys.FOLLOW_SYSTEM_THEME to false,
    PreferencesKeys.ZAPPING_MODE to false,
    PreferencesKeys.BRIGHTNESS_GESTURE to true,
    PreferencesKeys.VOLUME_GESTURE to true,
    PreferencesKeys.SCREENCAST to true,
    PreferencesKeys.SCREEN_ROTATING to false,
    PreferencesKeys.UNSEENS_MILLISECONDS to UnseensMilliseconds.DAYS_3,
    PreferencesKeys.RECONNECT_MODE to ReconnectMode.NO,
    PreferencesKeys.COLOR_ARGB to 0x5E6738,
    PreferencesKeys.TUNNELING to false,
    PreferencesKeys.CLOCK_MODE to false,
    PreferencesKeys.REMOTE_CONTROL to false,
    PreferencesKeys.SLIDER to true,
    PreferencesKeys.ALWAYS_SHOW_REPLAY to false,
    PreferencesKeys.PLAYER_PANEL to true,
    PreferencesKeys.COLORFUL_BACKGROUND to false,
    PreferencesKeys.COMPACT_DIMENSION to false
)
    .associateBy { it.key }
    .mapValues { it.value.value }

suspend fun Settings.applyDefaultValues() {
    if (applied.compareAndSet(false, true)) {
        edit { pref ->
            PREFERENCES.forEach { (key, defaultValue) ->
                if (key !in pref) {
                    pref.set<Any>(key as Preferences.Key<Any>, defaultValue as Any)
                }
            }
            if (PreferencesKeys.DEVICE_ID !in pref) {
                pref[PreferencesKeys.DEVICE_ID] = UUID.randomUUID().toString()
            }
        }
    }
}

private val applied = AtomicBoolean(false)

object PreferencesKeys {
    val DEVICE_ID = stringPreferencesKey("device_id")
    val PLAYLIST_STRATEGY = intPreferencesKey("playlist-strategy")
    val ROW_COUNT = intPreferencesKey("rowCount")

    val DEVICE_ID = stringPreferencesKey("device-id")

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

    val COLORFUL_BACKGROUND = booleanPreferencesKey("colorful-background")
    val COMPACT_DIMENSION = booleanPreferencesKey("compact-dimension")
}
