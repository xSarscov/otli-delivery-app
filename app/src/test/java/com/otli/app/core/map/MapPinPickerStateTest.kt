package com.otli.app.core.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MapPinPickerStateTest {
    @Test
    fun startsCentredOnNagaroteWithoutAPin() {
        val state = MapPinPickerState()

        assertThat(state.center).isEqualTo(MapPin(12.2656, -86.5664))
        assertThat(state.pin).isNull()
        assertThat(state.result).isNull()
    }

    @Test
    fun settingAPinExposesItAsTheResult() {
        val state = MapPinPickerState().withPin(12.27, -86.57)

        assertThat(state.pin).isEqualTo(MapPin(12.27, -86.57))
        assertThat(state.result).isEqualTo(MapPin(12.27, -86.57))
    }

    @Test
    fun aSecondPinReplacesTheFirst() {
        val state = MapPinPickerState().withPin(12.27, -86.57).withPin(12.30, -86.60)

        assertThat(state.result).isEqualTo(MapPin(12.30, -86.60))
    }

    @Test
    fun clearingRemovesThePinButKeepsTheCentre() {
        val state = MapPinPickerState().withPin(12.27, -86.57).cleared()

        assertThat(state.pin).isNull()
        assertThat(state.result).isNull()
        assertThat(state.center).isEqualTo(MapPinPickerState.NAGAROTE)
    }

    @Test
    fun anInitialPinRecentresTheMapOnIt() {
        val state = MapPinPickerState.startingAt(MapPin(12.5, -86.1))

        assertThat(state.center).isEqualTo(MapPin(12.5, -86.1))
        assertThat(state.result).isEqualTo(MapPin(12.5, -86.1))
    }

    @Test
    fun outOfRangeCoordinatesAreIgnored() {
        val state = MapPinPickerState().withPin(12.27, -86.57)

        assertThat(state.withPin(91.0, 0.0)).isEqualTo(state)
        assertThat(state.withPin(0.0, -181.0)).isEqualTo(state)
        assertThat(state.withPin(Double.NaN, 0.0)).isEqualTo(state)
    }
}
