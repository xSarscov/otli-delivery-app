package com.otli.app.dispatch.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PoolGateTest {
    @Test
    fun aCourierWithoutADocumentSeesNoPool() {
        assertThat(PoolGate.of(null)).isEqualTo(PoolGate.OFFLINE)
    }

    @Test
    fun anOfflineFreeCourierSeesNoPool() {
        assertThat(PoolGate.of(CourierAvailability(isOnline = false, activeOrderId = null))).isEqualTo(PoolGate.OFFLINE)
    }

    @Test
    fun anOnlineFreeCourierSeesThePool() {
        assertThat(PoolGate.of(CourierAvailability(isOnline = true, activeOrderId = null))).isEqualTo(PoolGate.OPEN)
    }

    @Test
    fun anOnlineCourierWithAnActiveOrderSeesNoPool() {
        assertThat(PoolGate.of(CourierAvailability(isOnline = true, activeOrderId = "o1"))).isEqualTo(PoolGate.BUSY)
    }

    @Test
    fun anActiveOrderWinsOverBeingOffline() {
        assertThat(PoolGate.of(CourierAvailability(isOnline = false, activeOrderId = "o1"))).isEqualTo(PoolGate.BUSY)
    }
}
