package com.otli.app.tracking.adapters.ui

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LocationPermissionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun showState() {
        compose.setContent {
            val permission = rememberLocationPermission()
            Text(if (permission.granted) "location granted" else "location denied")
        }
    }

    @Test
    fun itStartsDeniedWhenTheAppHoldsNoLocationPermission() {
        showState()

        compose.onNodeWithText("location denied").assertExists()
    }

    @Test
    fun itStartsGrantedWhenTheAppAlreadyHoldsTheFineLocationPermission() {
        shadowOf(compose.activity.application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

        showState()

        compose.onNodeWithText("location granted").assertExists()
    }
}
