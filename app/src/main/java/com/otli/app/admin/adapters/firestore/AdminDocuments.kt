package com.otli.app.admin.adapters.firestore

import com.google.firebase.firestore.FieldValue
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.UserAccount
import com.otli.app.core.money.Money
import com.otli.app.ordering.domain.OrderStatus

/**
 * Pure translation between the Admin's writes and reads and the documents they touch (`settings/app`,
 * `users/{uid}`, `merchants/{uid}`, `orders/{orderId}`, `couriers/{uid}`), free of snapshot objects so
 * it is JVM-testable. Every update carries exactly the fields the Firestore rules allow for that step.
 */
internal object AdminDocuments {
    const val SETTINGS = "settings"
    const val SETTINGS_APP = "app"
    const val USERS = "users"
    const val MERCHANTS = "merchants"

    fun feeData(fee: Money, adminUid: String): Map<String, Any> = mapOf(
        "deliveryFeeCents" to fee.centavos,
        "updatedAt" to FieldValue.serverTimestamp(),
        "updatedBy" to adminUid,
    )

    /** `users/{uid}` holds the account status (ADR-6); the rules let Admin change nothing else there. */
    fun userStatusUpdate(status: AccountStatus): Map<String, Any> = mapOf("status" to wire(status))

    /** The storefront copy of a merchant's status, written with the account in one batch. */
    fun merchantMirrorUpdate(status: AccountStatus): Map<String, Any> = mapOf(
        "status" to wire(status),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    /** Couriers have no mirror document: only merchants are listed in storefront queries. */
    fun hasMirror(role: Role): Boolean = role == Role.MERCHANT

    fun releaseOrderUpdate(): Map<String, Any?> = mapOf(
        "status" to OrderStatus.READY.wire,
        "courierId" to null,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun releaseCourierUpdate(): Map<String, Any?> = mapOf(
        "activeOrderId" to null,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun cancelUpdate(reason: String): Map<String, Any> = mapOf(
        "status" to OrderStatus.CANCELLED.wire,
        "cancelledBy" to "admin",
        "cancelReason" to reason,
        "cancelledAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    /** The courier whose slot a release frees: the one named on a `claimed` order, or null when there is none to free. */
    fun courierToFree(order: Map<String, Any?>?): String? = courierNamedOn(order, OrderStatus.CLAIMED)

    /**
     * The courier whose slot a cancellation frees: the one named on a `picked_up` order, whose cancellation
     * the rules pair with that slot. Any other order is cancelled alone.
     */
    fun courierToFreeOnCancel(order: Map<String, Any?>?): String? = courierNamedOn(order, OrderStatus.PICKED_UP)

    private fun courierNamedOn(order: Map<String, Any?>?, status: OrderStatus): String? {
        if (order?.get("status") != status.wire) return null
        return (order["courierId"] as? String)?.takeIf { it.isNotEmpty() }
    }

    /** Null for a document whose role or status is not one the app knows, so one bad document never breaks the list. */
    fun accountFrom(id: String, data: Map<String, Any?>): UserAccount? {
        val role = Role.entries.firstOrNull { wire(it) == data["role"] } ?: return null
        val status = AccountStatus.entries.firstOrNull { wire(it) == data["status"] } ?: return null
        return UserAccount(
            uid = id,
            role = role,
            status = status,
            displayName = data["displayName"] as? String ?: "",
            email = data["email"] as? String ?: "",
            phone = data["phone"] as? String ?: "",
        )
    }

    private fun wire(status: AccountStatus) = status.name.lowercase()

    private fun wire(role: Role) = role.name.lowercase()
}
