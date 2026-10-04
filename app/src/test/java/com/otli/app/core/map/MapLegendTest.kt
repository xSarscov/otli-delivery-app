package com.otli.app.core.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The labels of a map live in Compose under it, never in a native info window: what they say is decided here. */
class MapLegendTest {
    private val store = MapPin(12.2667, -86.5667)
    private val home = MapPin(12.27, -86.57)
    private val own = MapPin(12.268, -86.568)

    private fun courierScene(emphasis: MapEmphasis, courier: MapPin? = own) =
        MapScene(dropoff = home, courier = courier, pickup = store, emphasis = emphasis)

    @Test
    fun beforePickupTheCourierLegendStressesTheStoreAndMutesTheAddress() {
        val legend = courierScene(MapEmphasis.PICKUP).legend(viewerIsCourier = true, pickupDetail = "Mercado", dropoffDetail = "Casa azul")

        assertThat(legend).containsExactly(
            MapLegendEntry(MapLegendRole.PICKUP_HERE, MapMarkerLook.DESTINATION, emphasized = true, detail = "Mercado"),
            MapLegendEntry(MapLegendRole.DELIVERY_ADDRESS, MapMarkerLook.MUTED, emphasized = false, detail = "Casa azul"),
            MapLegendEntry(MapLegendRole.YOU, MapMarkerLook.COURIER, emphasized = false, detail = null),
        ).inOrder()
    }

    @Test
    fun afterPickupTheCourierLegendStressesTheAddressAndMutesTheStore() {
        val legend = courierScene(MapEmphasis.DROPOFF).legend(viewerIsCourier = true, pickupDetail = "Mercado", dropoffDetail = "Casa azul")

        assertThat(legend).containsExactly(
            MapLegendEntry(MapLegendRole.STORE, MapMarkerLook.MUTED, emphasized = false, detail = "Mercado"),
            MapLegendEntry(MapLegendRole.DELIVER_TO, MapMarkerLook.DESTINATION, emphasized = true, detail = "Casa azul"),
            MapLegendEntry(MapLegendRole.YOU, MapMarkerLook.COURIER, emphasized = false, detail = null),
        ).inOrder()
    }

    @Test
    fun theCourierLegendLeavesOutTheOwnPositionWhileItIsUnknown() {
        val legend = courierScene(MapEmphasis.PICKUP, courier = null).legend(viewerIsCourier = true)

        assertThat(legend.map { it.role }).containsExactly(MapLegendRole.PICKUP_HERE, MapLegendRole.DELIVERY_ADDRESS).inOrder()
    }

    @Test
    fun theCustomerLegendNamesTheAddressAndTheCourier() {
        val legend = MapScene(dropoff = home, courier = own).legend()

        assertThat(legend).containsExactly(
            MapLegendEntry(MapLegendRole.DELIVERY_ADDRESS, MapMarkerLook.DESTINATION, emphasized = true, detail = null),
            MapLegendEntry(MapLegendRole.COURIER, MapMarkerLook.COURIER, emphasized = false, detail = null),
        ).inOrder()
    }

    @Test
    fun theCustomerLegendWithoutACourierNamesOnlyTheAddress() {
        val legend = MapScene(dropoff = home).legend()

        assertThat(legend.map { it.role }).containsExactly(MapLegendRole.DELIVERY_ADDRESS)
    }
}
