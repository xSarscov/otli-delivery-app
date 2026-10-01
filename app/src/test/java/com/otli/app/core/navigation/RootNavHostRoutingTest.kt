package com.otli.app.core.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RootNavHostRoutingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val session = MutableStateFlow<SessionState>(SessionState.Loading)

    private val allTags = listOf(
        RootTags.LOADING,
        RootTags.SIGNED_OUT,
        RootTags.PROFILE_INCOMPLETE,
        RootTags.pending(Role.MERCHANT),
        RootTags.pending(Role.COURIER),
        RootTags.suspended(Role.MERCHANT),
        RootTags.suspended(Role.COURIER),
        RootTags.suspended(Role.CUSTOMER),
        RootTags.home(Role.CUSTOMER),
        RootTags.home(Role.MERCHANT),
        RootTags.home(Role.COURIER),
        RootTags.home(Role.ADMIN),
    )

    private fun launch() {
        compose.setContent {
            RootNavHost(
                session = session,
                pending = { role -> Text("pending", modifier = Modifier.testTag(RootTags.pending(role))) },
                suspended = { role -> Text("suspended", modifier = Modifier.testTag(RootTags.suspended(role))) },
            )
        }
        compose.waitForIdle()
    }

    /** Asserts that exactly [expected] is on screen and that every other route is unreachable. */
    private fun assertOnlyShowing(expected: String) {
        compose.onNodeWithTag(expected).assertIsDisplayed()
        for (tag in allTags - expected) {
            compose.onNodeWithTag(tag).assertDoesNotExist()
        }
    }

    @Test
    fun loadingShowsTheLoadingScreen() {
        session.value = SessionState.Loading
        launch()
        assertOnlyShowing(RootTags.LOADING)
    }

    @Test
    fun signedOutShowsTheSignedOutGraph() {
        session.value = SessionState.SignedOut
        launch()
        assertOnlyShowing(RootTags.SIGNED_OUT)
    }

    @Test
    fun profileIncompleteShowsItsGate() {
        session.value = SessionState.ProfileIncomplete
        launch()
        assertOnlyShowing(RootTags.PROFILE_INCOMPLETE)
    }

    @Test
    fun pendingMerchantAndCourierReachTheirPendingGateOnly() {
        session.value = SessionState.Pending(Role.MERCHANT)
        launch()
        assertOnlyShowing(RootTags.pending(Role.MERCHANT))

        session.value = SessionState.Pending(Role.COURIER)
        compose.waitForIdle()
        assertOnlyShowing(RootTags.pending(Role.COURIER))
    }

    @Test
    fun suspendedAccountsReachTheSuspendedGateOnly() {
        session.value = SessionState.Suspended(Role.COURIER)
        launch()
        assertOnlyShowing(RootTags.suspended(Role.COURIER))
    }

    @Test
    fun eachActiveRoleReachesItsOwnHomeAndNoOtherRoleHome() {
        for (role in Role.entries) {
            session.value = SessionState.Active(role)
            if (role == Role.CUSTOMER) launch() else compose.waitForIdle()
            assertOnlyShowing(RootTags.home(role))
        }
    }

    @Test
    fun approvalMovesAPendingMerchantIntoTheMerchantHomeLive() {
        session.value = SessionState.Pending(Role.MERCHANT)
        launch()
        assertOnlyShowing(RootTags.pending(Role.MERCHANT))

        session.value = SessionState.Active(Role.MERCHANT)
        compose.waitForIdle()
        assertOnlyShowing(RootTags.home(Role.MERCHANT))
    }

    @Test
    fun loggingOutRemovesTheRoleHomeFromTheScreen() {
        session.value = SessionState.Active(Role.ADMIN)
        launch()
        assertOnlyShowing(RootTags.home(Role.ADMIN))

        session.value = SessionState.SignedOut
        compose.waitForIdle()
        assertOnlyShowing(RootTags.SIGNED_OUT)
    }

    @Test
    fun suspensionRoutesAnActiveCustomerToTheSuspendedGate() {
        session.value = SessionState.Active(Role.CUSTOMER)
        launch()
        assertOnlyShowing(RootTags.home(Role.CUSTOMER))

        session.value = SessionState.Suspended(Role.CUSTOMER)
        compose.waitForIdle()
        assertOnlyShowing(RootTags.suspended(Role.CUSTOMER))
    }

    @Test
    fun anActiveMerchantSeesTheProvidedMerchantHomeAndNotThePlaceholder() {
        session.value = SessionState.Active(Role.MERCHANT)
        compose.setContent {
            RootNavHost(
                session = session,
                merchantHome = { Text("real merchant home", modifier = Modifier.testTag("merchant-home-slot")) },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("merchant-home-slot").assertIsDisplayed()
        compose.onNodeWithTag(RootTags.home(Role.MERCHANT)).assertDoesNotExist()
    }

    @Test
    fun otherRolesKeepTheirOwnHomeEvenWhenAMerchantHomeIsProvided() {
        session.value = SessionState.Active(Role.CUSTOMER)
        compose.setContent {
            RootNavHost(
                session = session,
                merchantHome = { Text("real merchant home", modifier = Modifier.testTag("merchant-home-slot")) },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag(RootTags.home(Role.CUSTOMER)).assertIsDisplayed()
        compose.onNodeWithTag("merchant-home-slot").assertDoesNotExist()
    }

    @Test
    fun anActiveCourierSeesTheProvidedCourierHomeAndNotThePlaceholder() {
        session.value = SessionState.Active(Role.COURIER)
        compose.setContent {
            RootNavHost(
                session = session,
                courierHome = { Text("real courier home", modifier = Modifier.testTag("courier-home-slot")) },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("courier-home-slot").assertIsDisplayed()
        compose.onNodeWithTag(RootTags.home(Role.COURIER)).assertDoesNotExist()
    }

    @Test
    fun otherRolesKeepTheirOwnHomeEvenWhenACourierHomeIsProvided() {
        session.value = SessionState.Active(Role.MERCHANT)
        compose.setContent {
            RootNavHost(
                session = session,
                courierHome = { Text("real courier home", modifier = Modifier.testTag("courier-home-slot")) },
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag(RootTags.home(Role.MERCHANT)).assertIsDisplayed()
        compose.onNodeWithTag("courier-home-slot").assertDoesNotExist()
    }
}
