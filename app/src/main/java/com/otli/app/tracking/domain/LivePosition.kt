package com.otli.app.tracking.domain

/**
 * The courier's latest published position as read back from `liveLocations/{orderId}`. [updatedAtMillis]
 * is the server's clock (epoch millis), so "last updated N s ago" does not depend on the courier's phone.
 */
data class LivePosition(
    val courierId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val updatedAtMillis: Long,
)
