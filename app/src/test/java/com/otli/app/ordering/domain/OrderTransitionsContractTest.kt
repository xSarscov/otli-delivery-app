package com.otli.app.ordering.domain

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * The transition table lives in two places that must never drift (ADR-14): this Kotlin object and
 * `backend/contracts/order-transitions.json`, which the rules tests iterate. This test pins the
 * Kotlin side to the fixture; the rules side is pinned by `order-transitions.test.ts`.
 */
class OrderTransitionsContractTest {

    private fun fixtureFile(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "backend/contracts/order-transitions.json")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("backend/contracts/order-transitions.json not found above ${System.getProperty("user.dir")}")
    }

    /** The fixture is flat and ours, so a per-object key lookup beats adding a JSON dependency. */
    private fun fixtureTriples(): Set<Triple<OrderStatus, OrderStatus, Actor>> {
        val text = fixtureFile().readText()
        val entries = Regex("""\{[^{}]*\}""").findAll(text).map { it.value }.toList()
        fun String.field(key: String): String =
            Regex(""""$key"\s*:\s*"([^"]+)"""").find(this)?.groupValues?.get(1) ?: error("missing $key in $this")
        assertThat(entries).hasSize(Regex(""""from"""").findAll(text).count())
        return entries.map { entry ->
            Triple(
                OrderStatus.fromWire(entry.field("from")) ?: error("unknown status in $entry"),
                OrderStatus.fromWire(entry.field("to")) ?: error("unknown status in $entry"),
                Actor.fromWire(entry.field("actor")) ?: error("unknown actor in $entry"),
            )
        }.toSet()
    }

    @Test
    fun theFixtureHoldsTheThirteenTriplesOfTheSpec() {
        assertThat(fixtureTriples()).hasSize(13)
    }

    @Test
    fun adminCancelsOnlyBeforeAnyCourierHoldsTheOrder() {
        val cancellable = listOf(OrderStatus.PLACED, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY)
        for (status in cancellable) {
            assertThat(OrderTransitions.isAllowed(status, OrderStatus.CANCELLED, Actor.ADMIN)).isTrue()
        }
        // A claimed order is released first; after pickup nothing can be undone.
        val others = OrderStatus.entries - cancellable.toSet()
        assertThat(others).isNotEmpty()
        for (status in others) {
            assertThat(OrderTransitions.isAllowed(status, OrderStatus.CANCELLED, Actor.ADMIN)).isFalse()
        }
    }

    @Test
    fun merchantsAndCouriersNeverCancelAnOrder() {
        for (actor in listOf(Actor.MERCHANT, Actor.COURIER)) {
            for (status in OrderStatus.entries) {
                assertThat(OrderTransitions.isAllowed(status, OrderStatus.CANCELLED, actor)).isFalse()
            }
        }
    }

    @Test
    fun theKotlinTableIsExactlyTheFixture() {
        assertThat(OrderTransitions.allowed).isEqualTo(fixtureTriples())
    }

    @Test
    fun isAllowedAgreesWithTheFixtureForEveryCombination() {
        val fixture = fixtureTriples()
        var checked = 0
        for (from in OrderStatus.entries) for (to in OrderStatus.entries) for (actor in Actor.entries) {
            assertThat(OrderTransitions.isAllowed(from, to, actor)).isEqualTo(Triple(from, to, actor) in fixture)
            checked++
        }
        assertThat(checked).isEqualTo(OrderStatus.entries.size * OrderStatus.entries.size * Actor.entries.size)
    }

    @Test
    fun theOwningMerchantStepsAnOrderThroughTheKitchenInSequence() {
        assertThat(OrderTransitions.isAllowed(OrderStatus.PLACED, OrderStatus.ACCEPTED, Actor.MERCHANT)).isTrue()
        assertThat(OrderTransitions.isAllowed(OrderStatus.ACCEPTED, OrderStatus.PREPARING, Actor.MERCHANT)).isTrue()
        assertThat(OrderTransitions.isAllowed(OrderStatus.PREPARING, OrderStatus.READY, Actor.MERCHANT)).isTrue()
    }

    @Test
    fun aMerchantCannotProgressAnOrderOutOfSequence() {
        assertThat(OrderTransitions.isAllowed(OrderStatus.PLACED, OrderStatus.READY, Actor.MERCHANT)).isFalse()
        assertThat(OrderTransitions.isAllowed(OrderStatus.PLACED, OrderStatus.PREPARING, Actor.MERCHANT)).isFalse()
        assertThat(OrderTransitions.isAllowed(OrderStatus.ACCEPTED, OrderStatus.READY, Actor.MERCHANT)).isFalse()
    }

    @Test
    fun aCustomerCancelsOnlyWhileTheOrderIsPlaced() {
        assertThat(OrderTransitions.isAllowed(OrderStatus.PLACED, OrderStatus.CANCELLED, Actor.CUSTOMER)).isTrue()
        val laterStatuses = OrderStatus.entries - OrderStatus.PLACED
        assertThat(laterStatuses).isNotEmpty()
        for (status in laterStatuses) {
            assertThat(OrderTransitions.isAllowed(status, OrderStatus.CANCELLED, Actor.CUSTOMER)).isFalse()
        }
    }

    @Test
    fun aCustomerCannotDriveTheKitchenOrTheDelivery() {
        assertThat(OrderTransitions.isAllowed(OrderStatus.PLACED, OrderStatus.ACCEPTED, Actor.CUSTOMER)).isFalse()
        assertThat(OrderTransitions.isAllowed(OrderStatus.READY, OrderStatus.CLAIMED, Actor.CUSTOMER)).isFalse()
        assertThat(OrderTransitions.isAllowed(OrderStatus.PICKED_UP, OrderStatus.DELIVERED, Actor.CUSTOMER)).isFalse()
    }

    @Test
    fun onlyAdminReleasesAClaimBackToReady() {
        assertThat(OrderTransitions.isAllowed(OrderStatus.CLAIMED, OrderStatus.READY, Actor.ADMIN)).isTrue()
        for (actor in Actor.entries - Actor.ADMIN) {
            assertThat(OrderTransitions.isAllowed(OrderStatus.CLAIMED, OrderStatus.READY, actor)).isFalse()
        }
    }

    @Test
    fun deliveredRejectedAndCancelledAreTerminalForEveryActor() {
        val terminal = listOf(OrderStatus.DELIVERED, OrderStatus.REJECTED, OrderStatus.CANCELLED)
        for (from in terminal) for (to in OrderStatus.entries) for (actor in Actor.entries) {
            assertThat(OrderTransitions.isAllowed(from, to, actor)).isFalse()
        }
        for (status in terminal) assertThat(status.isTerminal).isTrue()
        for (status in OrderStatus.entries - terminal.toSet()) assertThat(status.isTerminal).isFalse()
    }

    @Test
    fun wireNamesRoundTripAndUnknownNamesAreRefused() {
        for (status in OrderStatus.entries) assertThat(OrderStatus.fromWire(status.wire)).isEqualTo(status)
        for (actor in Actor.entries) assertThat(Actor.fromWire(actor.wire)).isEqualTo(actor)
        assertThat(OrderStatus.PICKED_UP.wire).isEqualTo("picked_up")
        assertThat(OrderStatus.fromWire("shipped")).isNull()
        assertThat(Actor.fromWire("superuser")).isNull()
    }
}
