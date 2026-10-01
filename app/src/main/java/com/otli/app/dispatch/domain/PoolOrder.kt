package com.otli.app.dispatch.domain

import com.otli.app.ordering.domain.OrderLocation
import com.otli.app.ordering.domain.Totals

/**
 * A `ready`, unclaimed order as an online courier sees it in the open pool. The dropoff is visible
 * before the claim: the rules cannot hide fields of a readable document (accepted risk, ADR-7 and
 * the read-rule notes of the dispatch slice).
 */
data class PoolOrder(
    val id: String,
    val merchantName: String,
    val pickup: OrderLocation,
    val dropoff: OrderLocation,
    /** The delivery fee is the courier's pay; the total is the cash to collect. */
    val totals: Totals,
    /** Epoch millis when the merchant marked the order ready; the pool lists the oldest first. */
    val readyAtMillis: Long,
)
