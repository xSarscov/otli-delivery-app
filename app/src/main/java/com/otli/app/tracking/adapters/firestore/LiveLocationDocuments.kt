package com.otli.app.tracking.adapters.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.otli.app.tracking.domain.GeoFix
import com.otli.app.tracking.domain.LivePosition

/**
 * Pure translation between `liveLocations/{orderId}` and the tracking domain, free of snapshot objects
 * so it is JVM-testable. The payload carries exactly the fields the Firestore rules allow
 * (`backend/contracts/live-location.json`).
 */
internal object LiveLocationDocuments {
    const val COLLECTION = "liveLocations"

    /** The device clock is deliberately not sent: the rules require `updatedAt` to be the server time. */
    fun publishPayload(courierId: String, fix: GeoFix): Map<String, Any> = mapOf(
        "courierId" to courierId,
        "lat" to fix.latitude,
        "lng" to fix.longitude,
        "accuracyM" to fix.accuracyMeters.toDouble(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    /** Null when any field is missing or of the wrong type, including a server timestamp still pending. */
    fun positionFrom(data: Map<String, Any?>): LivePosition? = LivePosition(
        courierId = data["courierId"] as? String ?: return null,
        latitude = (data["lat"] as? Number)?.toDouble() ?: return null,
        longitude = (data["lng"] as? Number)?.toDouble() ?: return null,
        accuracyMeters = (data["accuracyM"] as? Number)?.toFloat() ?: return null,
        updatedAtMillis = (data["updatedAt"] as? Timestamp)?.toDate()?.time ?: return null,
    )
}
