package com.otli.app.tracking.adapters.firestore

import com.google.common.truth.Truth.assertThat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.otli.app.tracking.domain.GeoFix
import com.otli.app.tracking.domain.LivePosition
import java.io.File
import org.junit.Test

/**
 * The shape of `liveLocations/{orderId}` lives in two places that must never drift: this adapter and
 * the Firestore rules, whose test iterates `backend/contracts/live-location.json`. This test pins the
 * adapter's payload to the same fixture.
 */
class LiveLocationDocumentsTest {
    private val fix = GeoFix(latitude = 12.27, longitude = -86.57, accuracyMeters = 8.5f, timestampMillis = 1_700_000_000_000L)

    private fun contractFields(): List<String> {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "backend/contracts/live-location.json")
            if (candidate.isFile) {
                val list = Regex(""""fields"\s*:\s*\[([^\]]*)]""").find(candidate.readText())?.groupValues?.get(1) ?: error("no fields in $candidate")
                return Regex(""""([^"]+)"""").findAll(list).map { it.groupValues[1] }.toList()
            }
            dir = dir.parentFile
        }
        error("backend/contracts/live-location.json not found above ${System.getProperty("user.dir")}")
    }

    @Test
    fun thePayloadHoldsExactlyTheFieldsOfTheContract() {
        val payload = LiveLocationDocuments.publishPayload("courier-1", fix)

        assertThat(contractFields()).hasSize(5)
        assertThat(payload.keys).containsExactlyElementsIn(contractFields())
    }

    @Test
    fun thePayloadCarriesTheCourierTheFixAndTheServerTime() {
        val payload = LiveLocationDocuments.publishPayload("courier-1", fix)

        assertThat(payload["courierId"]).isEqualTo("courier-1")
        assertThat(payload["lat"]).isEqualTo(12.27)
        assertThat(payload["lng"]).isEqualTo(-86.57)
        assertThat(payload["accuracyM"]).isEqualTo(8.5)
        assertThat(payload["updatedAt"]).isEqualTo(FieldValue.serverTimestamp())
    }

    @Test
    fun theDeviceClockNeverLeavesTheDevice() {
        val later = fix.copy(timestampMillis = fix.timestampMillis + 99_000)

        assertThat(LiveLocationDocuments.publishPayload("courier-1", later)).isEqualTo(LiveLocationDocuments.publishPayload("courier-1", fix))
    }

    @Test
    fun aStoredDocumentIsReadBackAsAPosition() {
        val stored = mapOf<String, Any?>(
            "courierId" to "courier-1", "lat" to 12.27, "lng" to -86.57, "accuracyM" to 8.5,
            "updatedAt" to Timestamp(1_700_000_005L, 0),
        )

        assertThat(LiveLocationDocuments.positionFrom(stored)).isEqualTo(
            LivePosition(courierId = "courier-1", latitude = 12.27, longitude = -86.57, accuracyMeters = 8.5f, updatedAtMillis = 1_700_000_005_000L),
        )
    }

    @Test
    fun wholeNumberCoordinatesStoredAsIntegersStillRead() {
        val stored = mapOf<String, Any?>(
            "courierId" to "courier-1", "lat" to 12L, "lng" to -86L, "accuracyM" to 0L,
            "updatedAt" to Timestamp(10L, 0),
        )

        val position = LiveLocationDocuments.positionFrom(stored)

        assertThat(position?.latitude).isEqualTo(12.0)
        assertThat(position?.longitude).isEqualTo(-86.0)
        assertThat(position?.accuracyMeters).isEqualTo(0f)
    }

    @Test
    fun aDocumentMissingAnyFieldIsNotAPosition() {
        val complete = mapOf<String, Any?>(
            "courierId" to "courier-1", "lat" to 12.27, "lng" to -86.57, "accuracyM" to 8.5,
            "updatedAt" to Timestamp(1_700_000_005L, 0),
        )

        for (field in complete.keys) {
            assertThat(LiveLocationDocuments.positionFrom(complete - field)).isNull()
        }
        assertThat(LiveLocationDocuments.positionFrom(complete + ("lat" to "12.27"))).isNull()
    }

    @Test
    fun aPositionWhoseServerTimestampIsStillPendingIsNotShown() {
        val pending = mapOf<String, Any?>("courierId" to "courier-1", "lat" to 12.27, "lng" to -86.57, "accuracyM" to 8.5, "updatedAt" to null)

        assertThat(LiveLocationDocuments.positionFrom(pending)).isNull()
    }
}
