package com.otli.app.tracking.adapters.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.otli.app.core.firebase.await
import com.otli.app.core.result.suspendRunCatching
import com.otli.app.tracking.application.LocationRepository
import com.otli.app.tracking.domain.GeoFix
import com.otli.app.tracking.domain.LivePosition
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * `liveLocations/{orderId}` (ADR-8). A rejected write (not the assigned courier, order not claimed or
 * picked up, less than 5 s since the last one) comes back as a failed [Result] carrying the Firestore
 * exception. Listener errors close the flow, so collectors must `catch`.
 */
class FirestoreLocationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : LocationRepository {

    override suspend fun publish(orderId: String, courierId: String, fix: GeoFix): Result<Unit> = suspendRunCatching {
        firestore.collection(LiveLocationDocuments.COLLECTION).document(orderId)
            .set(LiveLocationDocuments.publishPayload(courierId, fix))
            .await()
        Unit
    }

    override fun observe(orderId: String): Flow<LivePosition?> = callbackFlow {
        val registration = firestore.collection(LiveLocationDocuments.COLLECTION).document(orderId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else {
                    trySend(snapshot?.data?.let(LiveLocationDocuments::positionFrom))
                }
            }
        awaitClose { registration.remove() }
    }
}
