package com.otli.app.dispatch.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import org.junit.Test

class OwnPositionWantedTest {
    private val delivering = ActiveDeliveryUiState(isLoading = false, order = anOrder("o1", OrderStatus.CLAIMED, courierId = "courier-1"))

    @Test
    fun theCourierPositionIsWantedWhileADeliveryIsOnScreenAndTheLocationPermissionIsHeld() {
        assertThat(ownPositionWanted(permissionGranted = true, state = delivering)).isTrue()
        assertThat(ownPositionWanted(permissionGranted = true, state = delivering.copy(order = delivering.order!!.copy(status = OrderStatus.PICKED_UP)))).isTrue()
    }

    @Test
    fun withoutThePermissionItIsNotWanted() {
        assertThat(ownPositionWanted(permissionGranted = false, state = delivering)).isFalse()
    }

    @Test
    fun withoutADeliveryItIsNotWanted() {
        assertThat(ownPositionWanted(permissionGranted = true, state = ActiveDeliveryUiState(isLoading = false))).isFalse()
        assertThat(ownPositionWanted(permissionGranted = true, state = ActiveDeliveryUiState(isLoading = true))).isFalse()
    }
}
