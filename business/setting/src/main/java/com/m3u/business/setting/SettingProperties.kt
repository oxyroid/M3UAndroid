package com.m3u.business.setting

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.m3u.core.foundation.util.basic.PlaylistInputKind
import com.m3u.core.foundation.util.basic.sanitizePlaylistInput
import com.m3u.data.database.model.DataSource

class SettingProperties(
    val titleState: MutableState<String> = playlistInputState(PlaylistInputKind.TITLE),
    val urlState: MutableState<String> = playlistInputState(PlaylistInputKind.URL),
    val uriState: MutableState<Uri> = mutableStateOf(Uri.EMPTY),
    val localStorageState: MutableState<Boolean> = mutableStateOf(false),
    val forTvState: MutableState<Boolean> = mutableStateOf(false),
    val basicUrlState: MutableState<String> = playlistInputState(PlaylistInputKind.BASE_URL),
    val usernameState: MutableState<String> = playlistInputState(PlaylistInputKind.USERNAME),
    val passwordState: MutableState<String> = playlistInputState(PlaylistInputKind.PASSWORD),
    val epgState: MutableState<String> = playlistInputState(PlaylistInputKind.EPG_URL),
    val xtreamPlaylistTypeState: MutableState<String?> = mutableStateOf(null),
    val selectedState: MutableState<DataSource> = mutableStateOf(DataSource.M3U),
)

private fun playlistInputState(kind: PlaylistInputKind): MutableState<String> =
    SanitizedPlaylistInputState(kind)

@Stable
private class SanitizedPlaylistInputState(
    private val kind: PlaylistInputKind,
    private val delegate: MutableState<String> = mutableStateOf(""),
) : MutableState<String> {
    override var value: String
        get() = delegate.value
        set(value) {
            delegate.value = value.sanitizePlaylistInput(kind)
        }

    override fun component1(): String = value

    override fun component2(): (String) -> Unit = { value = it }
}
