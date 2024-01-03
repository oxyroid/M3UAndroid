package com.m3u.ui

import android.annotation.SuppressLint
import android.graphics.Rect
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.m3u.core.unspecified.UBoolean
import com.m3u.core.wrapper.Message

typealias OnUserLeaveHint = () -> Unit
typealias OnPipModeChanged = Consumer<PictureInPictureModeChangedInfo>

@Stable
interface Helper {
    var title: String
    var actions: List<Action>
    var fob: Fob?
    var statusBarVisibility: UBoolean
    var navigationBarVisibility: UBoolean
    var onUserLeaveHint: OnUserLeaveHint?
    var onPipModeChanged: OnPipModeChanged?
    var darkMode: UBoolean
    var brightness: Float
    val isInPipMode: Boolean
    var screenOrientation: Int

    @get:Composable
    val windowSizeClass: WindowSizeClass

    fun enterPipMode(size: Rect)
    fun toast(message: String)
    fun log(message: Message)
    fun play(url: String)
    fun replay()
}

val Helper.useRailNav: Boolean
    @Composable get() = windowSizeClass.widthSizeClass > WindowWidthSizeClass.Compact

private data class HelperBundle(
    val title: String,
    val actions: List<Action>,
    val fob: Fob?,
    val statusBarsVisibility: UBoolean,
    val navigationBarsVisibility: UBoolean,
    val onUserLeaveHint: (() -> Unit)?,
    val onPipModeChanged: Consumer<PictureInPictureModeChangedInfo>?,
    val darkMode: UBoolean
) {
    override fun toString(): String =
        "(title=$title,fob=$fob,status=$statusBarsVisibility,nav=$navigationBarsVisibility,dark=$darkMode)"
}

private fun Helper.restore(bundle: HelperBundle) {
    title = bundle.title
    actions = bundle.actions
    fob = bundle.fob
    statusBarVisibility = bundle.statusBarsVisibility
    navigationBarVisibility = bundle.navigationBarsVisibility
    onUserLeaveHint = bundle.onUserLeaveHint
    onPipModeChanged = bundle.onPipModeChanged
    darkMode = bundle.darkMode
}

private fun Helper.backup(): HelperBundle = HelperBundle(
    title = title,
    actions = actions,
    fob = fob,
    statusBarsVisibility = statusBarVisibility,
    navigationBarsVisibility = navigationBarVisibility,
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
    val lifecycleOwner = LocalLifecycleOwner.current
    check(state != Lifecycle.State.CREATED && state != Lifecycle.State.INITIALIZED) {
        "state cannot be CREATED or INITIALIZED!"
    }
    var bundle: HelperBundle? by remember { mutableStateOf(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.upTo(state) -> {
                    bundle = backup()
                    block()
                }

                Lifecycle.Event.downFrom(state) -> {
                    bundle?.let { restore(it) }
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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

    override var statusBarVisibility: UBoolean
        get() = error("Cannot get systemUiVisibility")
        set(_) {
            error("Cannot set systemUiVisibility")
        }
    override var navigationBarVisibility: UBoolean
        get() = error("Cannot get navigationBarsVisibility")
        set(_) {
            error("Cannot set navigationBarsVisibility")
        }

    override var darkMode: UBoolean
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

    override var brightness: Float
        get() = error("Cannot get brightness")
        set(_) {
            error("Cannot set brightness")
        }

    override val isInPipMode: Boolean
        get() = error("Cannot get isInPipMode")

    override var screenOrientation: Int
        get() = error("Cannot get screenOrientation")
        set(_) {
            error("Cannot set screenOrientation")
        }

    override val windowSizeClass: WindowSizeClass
        @Composable get() = error("Cannot get windowSizeClass")

    override fun enterPipMode(size: Rect) = error("Cannot enterPipMode")
    override fun toast(message: String) {
        error("Cannot toast: $message")
    }

    override fun log(message: Message) {
        error("Cannot snake: $message")
    }

    override fun play(url: String) {
        error("Cannot play stream: $url")
    }

    override fun replay() {
        error("Cannot replay")
    }
}

val LocalHelper = staticCompositionLocalOf { EmptyHelper }

@Immutable
data class Action(
    val icon: ImageVector,
    val contentDescription: String?,
    val onClick: () -> Unit
)

@Immutable
data class ActionHolder(
    val actions: List<Action>
)

@Composable
fun rememberActionHolder(actions: List<Action>): ActionHolder {
    return remember(actions) {
        ActionHolder(actions)
    }
}

@Immutable
data class Fob(
    val rootDestination: Destination.Root,
    val icon: ImageVector,
    val onClick: () -> Unit
)
