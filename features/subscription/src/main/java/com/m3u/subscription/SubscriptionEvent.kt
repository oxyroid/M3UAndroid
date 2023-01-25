package com.m3u.subscription

sealed interface SubscriptionEvent {
    data class GetDetails(val id: Int) : SubscriptionEvent
}
