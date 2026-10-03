package com.otli.app.core.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MapSceneTest {
    private val store = MapPin(12.2667, -86.5667)
    private val home = MapPin(12.27, -86.57)
    private val courier = MapPin(12.268, -86.568)

    private fun scene(
        emphasis: MapEmphasis = MapEmphasis.DROPOFF,
        pickup: MapPin? = store,
        courier: MapPin? = this.courier,
    ) = MapScene(dropoff = home, courier = courier, pickup = pickup, emphasis = emphasis)

    // --- what is the destination ---

    @Test
    fun beforePickupTheStoreIsTheDestination() {
        val scene = scene(MapEmphasis.PICKUP)

        assertThat(scene.destination).isEqualTo(store)
        assertThat(scene.pickupEmphasized).isTrue()
        assertThat(scene.dropoffEmphasized).isFalse()
    }

    @Test
    fun afterPickupTheCustomerIsTheDestination() {
        val scene = scene(MapEmphasis.DROPOFF)

        assertThat(scene.destination).isEqualTo(home)
        assertThat(scene.pickupEmphasized).isFalse()
        assertThat(scene.dropoffEmphasized).isTrue()
    }

    @Test
    fun withoutAPickupPinTheDropoffStaysTheDestinationEvenIfThePickupIsAsked() {
        val scene = scene(MapEmphasis.PICKUP, pickup = null)

        assertThat(scene.destination).isEqualTo(home)
        assertThat(scene.pickupEmphasized).isFalse()
        assertThat(scene.dropoffEmphasized).isTrue()
    }

    @Test
    fun theLiveMapOfTheCustomerHasOnlyTheDropoffAndTheCourier() {
        val scene = MapScene(dropoff = home, courier = courier)

        assertThat(scene.pickup).isNull()
        assertThat(scene.destination).isEqualTo(home)
        assertThat(scene.dropoffEmphasized).isTrue()
    }

    // --- what the camera fits ---

    @Test
    fun theCameraFitsTheCourierAndTheStoreBeforePickup() {
        assertThat(scene(MapEmphasis.PICKUP).framedPoints).containsExactly(store, courier).inOrder()
    }

    @Test
    fun theCameraFitsTheCourierAndTheDropoffAfterPickup() {
        assertThat(scene(MapEmphasis.DROPOFF).framedPoints).containsExactly(home, courier).inOrder()
    }

    @Test
    fun withoutAKnownPositionTheCameraFitsTheDestinationAlone() {
        assertThat(scene(MapEmphasis.PICKUP, courier = null).framedPoints).containsExactly(store)
        assertThat(scene(MapEmphasis.DROPOFF, courier = null).framedPoints).containsExactly(home)
    }

    // --- when the camera moves ---

    @Test
    fun theCameraIsReframedWhenTheEmphasisSwitchesOrTheCourierAppears() {
        val heading = scene(MapEmphasis.PICKUP).framingKey

        assertThat(scene(MapEmphasis.DROPOFF).framingKey).isNotEqualTo(heading)
        assertThat(scene(MapEmphasis.PICKUP, courier = null).framingKey).isNotEqualTo(heading)
    }

    @Test
    fun theCameraIsNotReframedWhenOnlyTheCourierMoves() {
        val before = scene(MapEmphasis.PICKUP).framingKey
        val moved = scene(MapEmphasis.PICKUP, courier = MapPin(12.2669, -86.5669)).framingKey

        assertThat(moved).isEqualTo(before)
    }
}
