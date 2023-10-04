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
    var systemUiVisibility: Boolean

    fun enterPipMode(size: Rect)
    fun detectDarkMode(handler: () -> Boolean)
    fun detectWindowInsetController(handler: WindowInsetsControllerCompat.() -> Unit)
    fun registerOnUserLeaveHintListener(callback: () -> Unit)
    fun unregisterOnUserLeaveHintListener()
    fun registerOnPictureInPictureModeChangedListener(
        consumer: Consumer<PictureInPictureModeChangedInfo>
    )

    fun unregisterOnPictureInPictureModeChangedListener()
}

@Composable
@SuppressLint("ComposableNaming")
fun Helper.repeatOnLifecycle(
    state: Lifecycle.State = Lifecycle.State.STARTED,
    block: Helper.() -> Unit
) {
    LifecycleEffect { event ->
        val title = title
        val actions = actions
        val fob = fob
        val systemUiVisibility = systemUiVisibility
        when (event) {
            Lifecycle.Event.upTo(state) -> block()
            Lifecycle.Event.downFrom(state) -> {
                this@repeatOnLifecycle.title = title
                this@repeatOnLifecycle.actions = actions
                this@repeatOnLifecycle.fob = fob
                this@repeatOnLifecycle.systemUiVisibility = systemUiVisibility
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

    override var systemUiVisibility: Boolean
        get() = error("Cannot get systemUiVisibility")
        set(_) {
            error("Cannot set systemUiVisibility")
        }

    override fun detectWindowInsetController(handler: WindowInsetsControllerCompat.() -> Unit) {
        error("detectWindowInsetController")
    }

    override fun enterPipMode(size: Rect) = error("Cannot enterPipMode")

    override fun registerOnPictureInPictureModeChangedListener(
        consumer: Consumer<PictureInPictureModeChangedInfo>
    ) = error("addOnPictureInPictureModeChangedListener")


    override fun unregisterOnPictureInPictureModeChangedListener() =
        error("removeOnPictureInPictureModeChangedListener")

    override fun registerOnUserLeaveHintListener(callback: () -> Unit) =
        error("addOnUserLeaveHintListener")

    override fun unregisterOnUserLeaveHintListener() = error("unregisterOnUserLeaveHintListener")
    override fun detectDarkMode(handler: () -> Boolean) {
        error("detectDarkMode")
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
data class Fob(
    val relation: TopLevelDestination,
    val icon: ImageVector,
    val onClick: () -> Unit
)