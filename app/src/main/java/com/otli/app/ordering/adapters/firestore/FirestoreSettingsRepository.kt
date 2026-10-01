package com.otli.app.ordering.adapters.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.otli.app.core.firebase.await
import com.otli.app.core.money.Money
import com.otli.app.core.result.suspendRunCatching
import com.otli.app.ordering.application.SettingsRepository
import javax.inject.Inject

/** `settings/app`: read-only for every signed-in user, written by Admin only. */
class FirestoreSettingsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : SettingsRepository {

    override suspend fun deliveryFee(): Result<Money> = suspendRunCatching {
        val cents = firestore.collection("settings").document("app").get().await().getLong("deliveryFeeCents")
        Money(requireNotNull(cents) { "settings/app has no deliveryFeeCents" })
    }
}
