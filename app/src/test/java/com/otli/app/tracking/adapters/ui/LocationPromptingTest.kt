package com.otli.app.tracking.adapters.ui

import android.app.PendingIntent
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.common.api.Status
import com.google.common.truth.Truth.assertThat
import com.otli.app.tracking.domain.LocationAction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocationPromptingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()

    private fun show(events: kotlinx.coroutines.flow.Flow<LocationAction>) {
        compose.setContent {
            LocationActionsEffect(
                events = events,
                onRequestPermission = { calls += "permission" },
                onTurnOnServices = { calls += "services" },
            )
        }
        compose.waitForIdle()
    }

    // --- events to dialogs ---

    @Test
    fun aPermissionRequestOpensThePermissionDialogOnly() {
        show(flowOf(LocationAction.REQUEST_PERMISSION))

        assertThat(calls).containsExactly("permission")
    }

    @Test
    fun aTurnOnRequestOpensTheLocationDialogOnly() {
        show(flowOf(LocationAction.TURN_ON_SERVICES))

        assertThat(calls).containsExactly("services")
    }

    @Test
    fun theRequestsAreAnsweredInOrderAndLateOnesToo() {
        val events = MutableSharedFlow<LocationAction>(extraBufferCapacity = 4)
        show(events)

        runBlocking {
            events.emit(LocationAction.REQUEST_PERMISSION)
            events.emit(LocationAction.TURN_ON_SERVICES)
        }
        compose.waitForIdle()

        assertThat(calls).containsExactly("permission", "services").inOrder()
    }

    // --- Play Services resolution ---

    private fun resolvable(): ResolvableApiException {
        val pending = PendingIntent.getActivity(compose.activity, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        return ResolvableApiException(Status(CommonStatusCodes.RESOLUTION_REQUIRED, null, pending))
    }

    @Test
    fun aResolvableFailureBecomesTheDialogToLaunch() {
        val request = resolvable().settingsResolution()

        assertThat(request).isNotNull()
        assertThat(request!!.intentSender).isNotNull()
    }

    @Test
    fun anyOtherFailureHasNoDialogToLaunch() {
        val unavailable = ApiException(Status(CommonStatusCodes.ERROR))

        assertThat(unavailable.settingsResolution()).isNull()
        assertThat(IllegalStateException("boom").settingsResolution()).isNull()
    }
}
