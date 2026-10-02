package com.otli.app.tracking.application

/**
 * Port over the thing that publishes the courier's position in the background (the foreground
 * service on Android). Both calls are idempotent: starting for the order that is already shared and
 * stopping when nothing runs change nothing.
 */
interface TrackingController {
    fun start(orderId: String, courierId: String)

    fun stop()
}
