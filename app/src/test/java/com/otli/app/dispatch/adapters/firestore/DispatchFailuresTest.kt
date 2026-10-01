package com.otli.app.dispatch.adapters.firestore

import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.otli.app.dispatch.domain.ClaimDecision
import com.otli.app.dispatch.domain.ClaimDenial
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Runs under Robolectric because [Code] needs the Android runtime to initialize. */
@RunWith(RobolectricTestRunner::class)
class DispatchFailuresTest {
    @Test
    fun aDeniedReadInsideTheClaimMeansSomeoneElseWon() {
        val failure = FirebaseFirestoreException("Missing or insufficient permissions.", Code.PERMISSION_DENIED)

        assertThat(DispatchDocuments.decisionForFailure(failure)).isEqualTo(ClaimDecision.Denied(ClaimDenial.ALREADY_CLAIMED))
    }

    @Test
    fun anyOtherFailureIsNotADecision() {
        assertThat(DispatchDocuments.decisionForFailure(FirebaseFirestoreException("offline", Code.UNAVAILABLE))).isNull()
        assertThat(DispatchDocuments.decisionForFailure(FirebaseFirestoreException("contention", Code.ABORTED))).isNull()
        assertThat(DispatchDocuments.decisionForFailure(IllegalStateException("boom"))).isNull()
    }
}
