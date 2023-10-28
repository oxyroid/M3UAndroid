package com.m3u.dlna.control

import com.m3u.dlna.Logger
import org.fourthline.cling.controlpoint.SubscriptionCallback
import org.fourthline.cling.model.gena.CancelReason
import org.fourthline.cling.model.gena.GENASubscription
import org.fourthline.cling.model.message.UpnpResponse
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.support.lastchange.LastChangeParser

internal class CastSubscriptionCallback(
    service: Service<*, *>?,
    requestedDurationSeconds: Int = 1800, // Cling default 1800
    private val lastChangeParser: LastChangeParser,
    private val callback: SubscriptionListener,
) : SubscriptionCallback(service, requestedDurationSeconds) {

    private val logger = Logger.create("SubscriptionCallback")

    override fun failed(
        subscription: GENASubscription<*>,
        responseStatus: UpnpResponse?,
        exception: Exception?,
        defaultMsg: String?
    ) {
        logger.e("${getTag(subscription)} failed:${responseStatus}, $exception, $defaultMsg")
        executeInMainThread { callback.failed(subscription.subscriptionId) }
    }

    override fun established(subscription: GENASubscription<*>) {
        logger.i("${getTag(subscription)} established")
        executeInMainThread { callback.established(subscription.subscriptionId) }
    }

    override fun ended(
        subscription: GENASubscription<*>,
        reason: CancelReason?,
        responseStatus: UpnpResponse?
    ) {
        logger.w("${getTag(subscription)} ended: $reason, $responseStatus")
        executeInMainThread { callback.ended(subscription.subscriptionId) }
    }

    override fun eventsMissed(subscription: GENASubscription<*>, numberOfMissedEvents: Int) {
        logger.w("${getTag(subscription)} eventsMissed: $numberOfMissedEvents")
    }

    override fun eventReceived(subscription: GENASubscription<*>) {
        val lastChangeEventValue = subscription.currentValues["LastChange"]?.value?.toString()
        if (lastChangeEventValue.isNullOrBlank()) return
        logger.i("${getTag(subscription)} eventReceived: ${subscription.currentValues.keys}")
        try {
            val events =
                lastChangeParser.parse(lastChangeEventValue)?.instanceIDs?.firstOrNull()?.values
            events?.forEach { value ->
                logger.i("    value: [${value.javaClass.simpleName}] $value")
                executeInMainThread { callback.onReceived(subscription.subscriptionId, value) }
            }
        } catch (e: Exception) {
            logger.w("${getTag(subscription)} currentValues: ${subscription.currentValues}")
            e.printStackTrace()
        }
    }

    private fun getTag(subscription: GENASubscription<*>) =
        "[${subscription.service.serviceType.type}](${
            subscription.subscriptionId?.split("-")?.last()
        })"
}