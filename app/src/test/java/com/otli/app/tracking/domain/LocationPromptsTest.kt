package com.otli.app.tracking.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocationPromptsTest {
    private val prompts = LocationPrompts()

    // --- going online ---

    @Test
    fun goingOnlineWithoutThePermissionAsksForIt() {
        assertThat(prompts.wentOnline(permitted = false, servicesOn = true)).isEqualTo(LocationAction.REQUEST_PERMISSION)
        assertThat(LocationPrompts().wentOnline(permitted = false, servicesOn = false)).isEqualTo(LocationAction.REQUEST_PERMISSION)
    }

    @Test
    fun goingOnlineWithThePermissionButTheLocationServicesOffAsksToTurnThemOn() {
        assertThat(prompts.wentOnline(permitted = true, servicesOn = false)).isEqualTo(LocationAction.TURN_ON_SERVICES)
    }

    @Test
    fun goingOnlineWithEverythingOnAsksForNothing() {
        assertThat(prompts.wentOnline(permitted = true, servicesOn = true)).isNull()
    }

    // --- the permission answer that follows ---

    @Test
    fun onceThePermissionAskedForOnGoingOnlineIsGrantedTheLocationServicesAreChecked() {
        prompts.wentOnline(permitted = false, servicesOn = false)

        assertThat(prompts.permissionChanged(permitted = true, servicesOn = false)).isEqualTo(LocationAction.TURN_ON_SERVICES)
    }

    @Test
    fun grantingThePermissionWithTheLocationServicesOnAsksForNothing() {
        prompts.wentOnline(permitted = false, servicesOn = true)

        assertThat(prompts.permissionChanged(permitted = true, servicesOn = true)).isNull()
    }

    @Test
    fun aPermissionChangeThatWasNotAskedForOnGoingOnlineDoesNotPrompt() {
        assertThat(prompts.permissionChanged(permitted = true, servicesOn = false)).isNull()
    }

    @Test
    fun revokingThePermissionDoesNotPrompt() {
        prompts.wentOnline(permitted = false, servicesOn = false)

        assertThat(prompts.permissionChanged(permitted = false, servicesOn = false)).isNull()
    }

    @Test
    fun theFollowUpHappensOnlyOncePerGoingOnline() {
        prompts.wentOnline(permitted = false, servicesOn = false)
        assertThat(prompts.permissionChanged(permitted = true, servicesOn = false)).isEqualTo(LocationAction.TURN_ON_SERVICES)

        assertThat(prompts.permissionChanged(permitted = true, servicesOn = false)).isNull()
    }

    // --- the location services going off during a delivery ---

    @Test
    fun whenTheLocationServicesGoOffDuringADeliveryTheCourierIsAskedToTurnThemOn() {
        assertThat(prompts.planChanged(TrackingPlan.Share("o1", "courier-1"))).isNull()

        assertThat(prompts.planChanged(TrackingPlan.ServicesOff("o1", "courier-1"))).isEqualTo(LocationAction.TURN_ON_SERVICES)
    }

    @Test
    fun theCourierIsNotAskedAgainWhileTheyStayOff() {
        prompts.planChanged(TrackingPlan.ServicesOff("o1", "courier-1"))

        assertThat(prompts.planChanged(TrackingPlan.ServicesOff("o1", "courier-1"))).isNull()
    }

    @Test
    fun aNewEpisodeOfTheLocationServicesBeingOffAsksAgain() {
        prompts.planChanged(TrackingPlan.ServicesOff("o1", "courier-1"))
        prompts.planChanged(TrackingPlan.Share("o1", "courier-1"))

        assertThat(prompts.planChanged(TrackingPlan.ServicesOff("o1", "courier-1"))).isEqualTo(LocationAction.TURN_ON_SERVICES)
    }

    @Test
    fun theOtherPlansNeverPrompt() {
        assertThat(prompts.planChanged(TrackingPlan.Idle)).isNull()
        assertThat(prompts.planChanged(TrackingPlan.NeedsPermission("o1"))).isNull()
        assertThat(prompts.planChanged(TrackingPlan.Share("o1", "courier-1"))).isNull()
    }
}
