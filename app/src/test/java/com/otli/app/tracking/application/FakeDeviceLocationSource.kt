package com.otli.app.tracking.application

import com.otli.app.tracking.domain.GeoFix
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * A device that behaves like the one observed on the phone: a location request made while the location
 * services are off stays silent for good, even after they come back on; only a request made while they are
 * on delivers fixes. [requests] counts how many times somebody asked the device for its position, and
 * [listeners] how many of those are currently collecting.
 */
class FakeDeviceLocationSource(private val settings: FakeLocationSettingsChecker) : LocationSource {
    private val shared = MutableSharedFlow<GeoFix>(extraBufferCapacity = 64)

    var requests = 0
        private set

    var listeners = 0
        private set

    /** The device produces a fix; whoever asked while the services were on receives it. */
    fun emit(fix: GeoFix) {
        shared.tryEmit(fix)
    }

    override fun fixes(): Flow<GeoFix> = flow {
        requests++
        listeners++
        try {
            if (!settings.enabled.value) awaitCancellation()
            emitAll(shared)
        } finally {
            listeners--
        }
    }
}
