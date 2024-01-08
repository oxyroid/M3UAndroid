package com.m3u.dlna.control

import android.os.Handler
import android.os.Looper
import com.m3u.dlna.control.action.SetNextAVTransportURI
import org.jupnp.controlpoint.ActionCallback
import org.jupnp.controlpoint.ControlPoint
import org.jupnp.model.action.ActionInvocation
import org.jupnp.model.message.UpnpResponse
import org.jupnp.model.meta.Service
import org.jupnp.support.avtransport.callback.GetMediaInfo
import org.jupnp.support.avtransport.callback.GetPositionInfo
import org.jupnp.support.avtransport.callback.GetTransportInfo
import org.jupnp.support.avtransport.callback.Next
import org.jupnp.support.avtransport.callback.Pause
import org.jupnp.support.avtransport.callback.Play
import org.jupnp.support.avtransport.callback.Previous
import org.jupnp.support.avtransport.callback.Seek
import org.jupnp.support.avtransport.callback.SetAVTransportURI
import org.jupnp.support.avtransport.callback.Stop
import org.jupnp.support.contentdirectory.callback.Browse
import org.jupnp.support.contentdirectory.callback.Search
import org.jupnp.support.lastchange.LastChangeParser
import org.jupnp.support.model.BrowseFlag
import org.jupnp.support.model.DIDLContent
import org.jupnp.support.model.MediaInfo
import org.jupnp.support.model.PositionInfo
import org.jupnp.support.model.TransportInfo
import org.jupnp.support.renderingcontrol.callback.GetMute
import org.jupnp.support.renderingcontrol.callback.GetVolume
import org.jupnp.support.renderingcontrol.callback.SetMute
import org.jupnp.support.renderingcontrol.callback.SetVolume
import java.util.Formatter
import java.util.Locale

private object Actions {
    // AvTransport
    const val SetAVTransportURI = "SetAVTransportURI"
    const val SetNextAVTransportURI = "SetNextAVTransportURI"
    const val Play = "Play"
    const val Pause = "Pause"
    const val Stop = "Stop"
    const val Seek = "Seek"
    const val Next = "Next"
    const val Previous = "Previous"
    const val GetPositionInfo = "GetPositionInfo"
    const val GetMediaInfo = "GetMediaInfo"
    const val GetTransportInfo = "GetTransportInfo"

    // Renderer
    const val SetVolume = "SetVolume"
    const val GetVolume = "GetVolume"
    const val SetMute = "SetMute"
    const val GetMute = "GetMute"
}


internal class ActionCallbackWrapper(
    private val actionCallback: ActionCallback,
) : ActionCallback(actionCallback.actionInvocation) {
    override fun success(invocation: ActionInvocation<out Service<*, *>>?) {
        actionCallback.success(invocation)
    }

    override fun failure(
        invocation: ActionInvocation<out Service<*, *>>?,
        operation: UpnpResponse?,
        defaultMsg: String?
    ) {
        actionCallback.failure(invocation, operation, defaultMsg)
    }
}

internal abstract class BaseServiceExecutor(
    private val controlPoint: ControlPoint,
    protected val service: Service<*, *>?,
) {
    private val handler = Handler(Looper.getMainLooper())

    protected fun invalidServiceAction(actionName: String): Boolean {
        return service?.getAction(actionName) == null
    }

    protected fun executeAction(actionCallback: ActionCallback) {
        controlPoint.execute(ActionCallbackWrapper(actionCallback))
    }

    fun subscribe(subscriptionCallback: SubscriptionListener, lastChangeParser: LastChangeParser) {
        controlPoint.execute(
            CastSubscriptionCallback(
                service,
                callback = subscriptionCallback,
                lastChangeParser = lastChangeParser
            )
        )
    }

    protected fun <T> notifySuccess(listener: ServiceActionCallback<T>?, result: T) {
        listener?.run { notify { onSuccess(result) } }
    }

    protected fun <T> notifyFailure(
        listener: ServiceActionCallback<T>?,
        exception: String = "Service not support this action."
    ) {
        listener?.run { notify { onFailure(exception) } }
    }

    private fun notify(runnable: Runnable) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(runnable)
        } else {
            runnable.run()
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // AvService
    // ---------------------------------------------------------------------------------------------------------
    internal class AVServiceExecutorImpl(
        controlPoint: ControlPoint,
        service: Service<*, *>?,
    ) : BaseServiceExecutor(controlPoint, service), AvTransportServiceAction {
        override fun setAVTransportURI(
            uri: String,
            title: String,
            callback: ServiceActionCallback<Unit>?
        ) {
            if (invalidServiceAction(Actions.SetAVTransportURI)) {
                notifyFailure(callback)
                return
            }
            val metadata = MetadataUtils.create(uri, title)
            executeAction(object : SetAVTransportURI(service, uri, metadata) {
                override fun success(invocation: ActionInvocation<*>?) {
                    notifySuccess(callback, result = Unit)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun setNextAVTransportURI(
            uri: String,
            title: String,
            callback: ServiceActionCallback<Unit>?
        ) {
            if (invalidServiceAction(Actions.SetNextAVTransportURI)) {
                notifyFailure(callback)
                return
            }
            val metadata = MetadataUtils.create(uri, title)
            executeAction(object :
                SetNextAVTransportURI(service = service, uri = uri, metadata = metadata) {
                override fun success(invocation: ActionInvocation<*>?) {
                    notifySuccess(callback, result = Unit)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun play(speed: String, callback: ServiceActionCallback<Unit>?) {
            if (invalidServiceAction(Actions.Play)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : Play(service, speed) {
                override fun success(invocation: ActionInvocation<*>?) {
                    notifySuccess(callback, result = Unit)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun pause(callback: ServiceActionCallback<Unit>?) {
            if (invalidServiceAction(Actions.Pause)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : Pause(service) {
                override fun success(invocation: ActionInvocation<*>?) {
                    notifySuccess(callback, result = Unit)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun stop(callback: ServiceActionCallback<Unit>?) {
            if (invalidServiceAction(Actions.Stop)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : Stop(service) {
                override fun success(invocation: ActionInvocation<*>?) {
                    notifySuccess(callback, result = Unit)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun seek(millSeconds: Long, callback: ServiceActionCallback<Unit>?) {
            if (invalidServiceAction(Actions.Seek)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : Seek(service, getStringTime(millSeconds)) {
                override fun success(invocation: ActionInvocation<*>?) {
                    notifySuccess(callback, result = Unit)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun next(callback: ServiceActionCallback<Unit>?) {
            if (invalidServiceAction(Actions.Next)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : Next(service) {
                override fun success(invocation: ActionInvocation<*>?) {
                    notifySuccess(callback, result = Unit)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun previous(callback: ServiceActionCallback<Unit>?) {
            if (invalidServiceAction(Actions.Previous)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : Previous(service) {
                override fun success(invocation: ActionInvocation<*>?) {
                    notifySuccess(callback, result = Unit)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun getPositionInfo(callback: ServiceActionCallback<PositionInfo>?) {
            //logger.i(Actions.GetPositionInfo)
            if (invalidServiceAction(Actions.GetPositionInfo)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : GetPositionInfo(service) {
                override fun received(
                    invocation: ActionInvocation<*>?,
                    positionInfo: PositionInfo
                ) {
                    notifySuccess(callback, result = positionInfo)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun getMediaInfo(callback: ServiceActionCallback<MediaInfo>?) {
            if (invalidServiceAction(Actions.GetMediaInfo)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : GetMediaInfo(service) {
                override fun received(invocation: ActionInvocation<*>?, mediaInfo: MediaInfo) {
                    notifySuccess(callback, result = mediaInfo)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun getTransportInfo(callback: ServiceActionCallback<TransportInfo>?) {
            if (invalidServiceAction(Actions.GetTransportInfo)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : GetTransportInfo(service) {
                override fun received(
                    invocation: ActionInvocation<*>?,
                    transportInfo: TransportInfo
                ) {
                    notifySuccess(callback, result = transportInfo)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }
    }

    internal class RendererServiceExecutorImpl(
        controlPoint: ControlPoint,
        service: Service<*, *>?,
    ) : BaseServiceExecutor(controlPoint, service), RendererServiceAction {
        override fun setVolume(volume: Int, callback: ServiceActionCallback<Unit>?) {
            if (invalidServiceAction(Actions.SetVolume)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : SetVolume(service, volume.toLong()) {
                override fun success(invocation: ActionInvocation<*>?) {
                    notifySuccess(callback, result = Unit)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun getVolume(callback: ServiceActionCallback<Int>?) {
            if (invalidServiceAction(Actions.GetVolume)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : GetVolume(service) {
                override fun received(invocation: ActionInvocation<*>?, currentVolume: Int) {
                    notifySuccess(callback, result = currentVolume)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun setMute(mute: Boolean, callback: ServiceActionCallback<Unit>?) {
            if (invalidServiceAction(Actions.SetMute)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : SetMute(service, mute) {
                override fun success(invocation: ActionInvocation<*>?) {
                    notifySuccess(callback, result = Unit)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun getMute(callback: ServiceActionCallback<Boolean>?) {
            if (invalidServiceAction(Actions.GetMute)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : GetMute(service) {
                override fun received(invocation: ActionInvocation<*>?, currentMute: Boolean) {
                    notifySuccess(callback, result = currentMute)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }
    }

    internal class ContentServiceExecutorImpl(
        controlPoint: ControlPoint,
        service: Service<*, *>?,
    ) : BaseServiceExecutor(controlPoint, service), ContentServiceAction {
        override fun browse(
            objectId: String,
            flag: BrowseFlag,
            filter: String,
            firstResult: Int,
            maxResults: Int,
            callback: ServiceActionCallback<DIDLContent>?
        ) {
            if (invalidServiceAction("Browse")) {
                notifyFailure(callback)
                return
            }
            executeAction(object :
                Browse(service, objectId, flag, filter, firstResult.toLong(), maxResults.toLong()) {
                override fun received(
                    actionInvocation: ActionInvocation<out Service<*, *>>?,
                    didl: DIDLContent
                ) {
                    notifySuccess(callback, result = didl)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }

                override fun updateStatus(status: Status?) {}
            })
        }

        override fun search(
            containerId: String,
            searchCriteria: String,
            filter: String,
            firstResult: Int,
            maxResults: Int,
            callback: ServiceActionCallback<DIDLContent>?
        ) {
            if (invalidServiceAction("Search")) {
                notifyFailure(callback)
                return
            }
            executeAction(object : Search(
                service,
                containerId,
                searchCriteria,
                filter,
                firstResult.toLong(),
                maxResults.toLong()
            ) {
                override fun received(
                    actionInvocation: ActionInvocation<out Service<*, *>>?,
                    didl: DIDLContent
                ) {
                    notifySuccess(callback, result = didl)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }

                override fun updateStatus(status: Status?) {}
            })
        }
    }
}

private fun getStringTime(timeMs: Long): String {
    val formatter = Formatter(StringBuilder(), Locale.US)
    val totalSeconds = timeMs / 1000
    val seconds = totalSeconds % 60
    val minutes = totalSeconds / 60 % 60
    val hours = totalSeconds / 3600
    return formatter.format("%02d:%02d:%02d", hours, minutes, seconds).toString()
}