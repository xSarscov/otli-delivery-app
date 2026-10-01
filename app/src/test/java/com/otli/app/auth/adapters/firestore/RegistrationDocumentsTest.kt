package com.otli.app.auth.adapters.firestore

import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FieldValue
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.MerchantStoreDetails
import com.otli.app.auth.domain.Role
import org.junit.Test

class RegistrationDocumentsTest {
    private val store = MerchantStoreDetails("Pulperia Test", "+50588880000", 12.2656, -86.5664)

    // --- the courier slot document ---

    @Test
    fun aCourierRegistersWithItsOwnOfflineFreeCourierDocument() {
        val documents = RegistrationDocuments.profileDocuments(Role.COURIER, AccountStatus.PENDING, merchantStore = null)

        assertThat(documents.map { it.collection }).containsExactly("couriers")
        val data = documents.single().data
        assertThat(data.keys).containsExactly("isOnline", "activeOrderId", "updatedAt")
        assertThat(data["isOnline"]).isEqualTo(false)
        assertThat(data["activeOrderId"]).isNull()
        assertThat(data["updatedAt"]).isEqualTo(FieldValue.serverTimestamp())
    }

    @Test
    fun theCourierDocumentDoesNotDependOnTheAccountStatus() {
        val pending = RegistrationDocuments.profileDocuments(Role.COURIER, AccountStatus.PENDING, null).single()
        val active = RegistrationDocuments.profileDocuments(Role.COURIER, AccountStatus.ACTIVE, null).single()

        assertThat(active.data.keys).isEqualTo(pending.data.keys)
        assertThat(active.data["isOnline"]).isEqualTo(false)
    }

    @Test
    fun aCourierRegistrationIgnoresStoreDetails() {
        val documents = RegistrationDocuments.profileDocuments(Role.COURIER, AccountStatus.PENDING, store)

        assertThat(documents.map { it.collection }).containsExactly("couriers")
    }

    // --- the merchant document keeps its shape ---

    @Test
    fun aMerchantRegistersWithItsPendingClosedStoreProfile() {
        val documents = RegistrationDocuments.profileDocuments(Role.MERCHANT, AccountStatus.PENDING, store)

        assertThat(documents.map { it.collection }).containsExactly("merchants")
        val data = documents.single().data
        assertThat(data.keys).containsExactly("name", "description", "phone", "status", "isOpen", "location", "createdAt", "updatedAt")
        assertThat(data["name"]).isEqualTo("Pulperia Test")
        assertThat(data["description"]).isEqualTo("")
        assertThat(data["phone"]).isEqualTo("+50588880000")
        assertThat(data["status"]).isEqualTo("pending")
        assertThat(data["isOpen"]).isEqualTo(false)
        assertThat(data["location"]).isEqualTo(mapOf("lat" to 12.2656, "lng" to -86.5664, "reference" to ""))
    }

    @Test
    fun aMerchantWithoutStoreDetailsGetsNoProfileDocument() {
        assertThat(RegistrationDocuments.profileDocuments(Role.MERCHANT, AccountStatus.PENDING, merchantStore = null)).isEmpty()
    }

    // --- everyone else ---

    @Test
    fun customersAndAdminsGetNoExtraDocument() {
        assertThat(RegistrationDocuments.profileDocuments(Role.CUSTOMER, AccountStatus.ACTIVE, null)).isEmpty()
        assertThat(RegistrationDocuments.profileDocuments(Role.CUSTOMER, AccountStatus.ACTIVE, store)).isEmpty()
        assertThat(RegistrationDocuments.profileDocuments(Role.ADMIN, AccountStatus.ACTIVE, null)).isEmpty()
    }
}
