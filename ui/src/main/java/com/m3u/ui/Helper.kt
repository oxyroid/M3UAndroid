package com.m3u.ui

import android.annotation.SuppressLint
import android.graphics.Rect
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import com.m3u.core.unspecified.UBoolean
import com.m3u.material.ktx.LifecycleEffect

typealias OnUserLeaveHint = () -> Unit
typealias OnPipModeChanged = Consumer<PictureInPictureModeChangedInfo>

@Stable
interface Helper {
    var title: String
    var actions: List<Action>
    var fob: Fob?
    var statusBarsVisibility: UBoolean
    var navigationBarsVisibility: UBoolean
    var onUserLeaveHint: OnUserLeaveHint?
    var onPipModeChanged: OnPipModeChanged?
    var darkMode: Boolean

    fun enterPipMode(size: Rect)
    fun toast(message: String)
    fun snake(message: String)
}

private data class HelperBundle(
    val title: String,
    val actions: List<Action>,
    val fob: Fob?,
    val statusBarsVisibility: UBoolean,
    val navigationBarsVisibility: UBoolean,
    val onUserLeaveHint: (() -> Unit)?,
    val onPipModeChanged: Consumer<PictureInPictureModeChangedInfo>?,
    val darkMode: Boolean
) {
    override fun toString(): String =
        "(title=$title,fob=$fob,status=$statusBarsVisibility,nav=$navigationBarsVisibility,dark=$darkMode)"
}

private fun Helper.restore(bundle: HelperBundle) {
    title = bundle.title
    actions = bundle.actions
    fob = bundle.fob
    statusBarsVisibility = bundle.statusBarsVisibility
    navigationBarsVisibility = bundle.navigationBarsVisibility
    onUserLeaveHint = bundle.onUserLeaveHint
    onPipModeChanged = bundle.onPipModeChanged
    darkMode = bundle.darkMode
}

private fun Helper.backup(): HelperBundle = HelperBundle(
    title = title,
    actions = actions,
    fob = fob,
    statusBarsVisibility = statusBarsVisibility,
    navigationBarsVisibility = navigationBarsVisibility,
    onUserLeaveHint = onUserLeaveHint,
    onPipModeChanged = onPipModeChanged,
    darkMode = darkMode
)

@Composable
@SuppressLint("ComposableNaming")
fun Helper.repeatOnLifecycle(
    state: Lifecycle.State = Lifecycle.State.STARTED,
    block: Helper.() -> Unit
) {
    check(state != Lifecycle.State.CREATED && state != Lifecycle.State.INITIALIZED) {
        "state cannot be CREATED or INITIALIZED!"
    }
    var bundle: HelperBundle? = null
    LifecycleEffect { event ->
        when (event) {
            Lifecycle.Event.upTo(state) -> {
                bundle = backup()
                Log.d("Helper", "repeatOnLifecycle: backup -> $bundle")
                block()
            }

            Lifecycle.Event.downFrom(state) -> {
                Log.d("Helper", "repeatOnLifecycle: restore -> $bundle")
                bundle?.let(::restore)
            }

            else -> {}
        }
    }
}

val EmptyHelper = object : Helper {
    override var title: String
        get() = error("Cannot get title")
        set(_) {
            error("Cannot set title")
        }

    override var actions: List<Action>
        get() = error("Cannot get actions")
        set(_) {
            error("Cannot set actions")
        }
    override var fob: Fob?
        get() = error("Cannot get fob")
        set(_) {
            error("Cannot set fob")
        }

    override var statusBarsVisibility: UBoolean
        get() = error("Cannot get systemUiVisibility")
        set(_) {
            error("Cannot set systemUiVisibility")
        }
    override var navigationBarsVisibility: UBoolean
        get() = error("Cannot get navigationBarsVisibility")
        set(_) {
            error("Cannot set navigationBarsVisibility")
        }

    override var darkMode: Boolean
        get() = error("Cannot get darkMode")
        set(_) {
            error("Cannot set darkMode")
        }

    override var onUserLeaveHint: OnUserLeaveHint?
        get() = error("Cannot get onUserLeaveHint")
        set(_) {
            error("Cannot set onUserLeaveHint")
        }
    override var onPipModeChanged: OnPipModeChanged?
        get() = error("Cannot get onPipModeChanged")
        set(_) {
            error("Cannot set onPipModeChanged")
        }

    override fun enterPipMode(size: Rect) = error("Cannot enterPipMode")
    override fun toast(message: String) {
        error("Cannot toast: $message")
    }

    override fun snake(message: String) {
        error("Cannot snake: $message")
    }
}

val LocalHelper = staticCompositionLocalOf { EmptyHelper }

@Immutable
data class Action(
    val icon: ImageVector,
    val contentDescription: String?,
    val onClick: () -> Unit
)

typealias ActionsFactory = () -> List<Action>

@Immutable
data class Fob(
    val rootDestination: Destination.Root,
    val icon: ImageVector,
    val onClick: () -> Unit
)
