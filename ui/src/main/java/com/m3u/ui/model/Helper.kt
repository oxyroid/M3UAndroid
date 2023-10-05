package com.m3u.ui.model

import android.annotation.SuppressLint
import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import com.m3u.ui.TopLevelDestination
import com.m3u.ui.ktx.LifecycleEffect

@Stable
interface Helper {
    var title: String
    var actions: List<Action>
    var fob: Fob?
    var statusBarsVisibility: Boolean
    var navigationBarsVisibility: Boolean
    var onUserLeaveHint: (() -> Unit)?
    var onPipModeChanged: Consumer<PictureInPictureModeChangedInfo>?
    var darkMode: Boolean

    fun enterPipMode(size: Rect)
    fun detectWindowInsetController(handler: WindowInsetsControllerCompat.() -> Unit)
}

private data class HelperBundle(
    val title: String,
    val actions: List<Action>,
    val fob: Fob?,
    val systemUiVisibility: Boolean,
    val onUserLeaveHint: (() -> Unit)?,
    val onPipModeChanged: Consumer<PictureInPictureModeChangedInfo>?,
    val darkMode: Boolean
)

private fun Helper.restore(bundle: HelperBundle) {
    title = bundle.title
    actions = bundle.actions
    fob = bundle.fob
    statusBarsVisibility = bundle.systemUiVisibility
    onUserLeaveHint = bundle.onUserLeaveHint
    onPipModeChanged = bundle.onPipModeChanged
    darkMode = bundle.darkMode
}

private fun Helper.backup(): HelperBundle = HelperBundle(
    title = title,
    actions = actions,
    fob = fob,
    systemUiVisibility = statusBarsVisibility,
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
                block()
            }

            Lifecycle.Event.downFrom(state) -> {
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

    override var statusBarsVisibility: Boolean
        get() = error("Cannot get systemUiVisibility")
        set(_) {
            error("Cannot set systemUiVisibility")
        }
    override var navigationBarsVisibility: Boolean
        get() = error("Cannot get navigationBarsVisibility")
        set(_) {
            error("Cannot set navigationBarsVisibility")
        }

    override var darkMode: Boolean
        get() = error("Cannot get darkMode")
        set(_) {
            error("Cannot set darkMode")
        }

    override var onUserLeaveHint: (() -> Unit)?
        get() = error("Cannot get onUserLeaveHint")
        set(_) {
            error("Cannot set onUserLeaveHint")
        }
    override var onPipModeChanged: Consumer<PictureInPictureModeChangedInfo>?
        get() = error("Cannot get onPipModeChanged")
        set(_) {
            error("Cannot set onPipModeChanged")
        }

    override fun detectWindowInsetController(handler: WindowInsetsControllerCompat.() -> Unit) {
        error("detectWindowInsetController")
    }

    override fun enterPipMode(size: Rect) = error("Cannot enterPipMode")
}

val LocalHelper = staticCompositionLocalOf { EmptyHelper }

@Immutable
data class Action(
    val icon: ImageVector,
    val contentDescription: String?,
    val onClick: () -> Unit
)

@Immutable
data class Fob(
    val relation: TopLevelDestination,
    val icon: ImageVector,
    val onClick: () -> Unit
)