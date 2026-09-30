package com.otli.app.auth.domain

import com.google.common.truth.Truth.assertThat
import com.otli.app.core.result.DomainError
import org.junit.Test

class RegistrationPolicyTest {
    @Test
    fun customerRegistersActive() {
        assertThat(RegistrationPolicy.initialStatus(Role.CUSTOMER))
            .isEqualTo(RegistrationDecision.Accepted(AccountStatus.ACTIVE))
    }

    @Test
    fun merchantRegistersPending() {
        assertThat(RegistrationPolicy.initialStatus(Role.MERCHANT))
            .isEqualTo(RegistrationDecision.Accepted(AccountStatus.PENDING))
    }

    @Test
    fun courierRegistersPending() {
        assertThat(RegistrationPolicy.initialStatus(Role.COURIER))
            .isEqualTo(RegistrationDecision.Accepted(AccountStatus.PENDING))
    }

    @Test
    fun adminIsRejectedWithTypedError() {
        val decision = RegistrationPolicy.initialStatus(Role.ADMIN)
        assertThat(decision).isInstanceOf(RegistrationDecision.Rejected::class.java)
        assertThat((decision as RegistrationDecision.Rejected).error)
            .isInstanceOf(DomainError.Unauthorized::class.java)
    }

    @Test
    fun onlyNonAdminRolesAreSelfRegistrable() {
        assertThat(RegistrationPolicy.selfRegistrableRoles)
            .containsExactly(Role.CUSTOMER, Role.MERCHANT, Role.COURIER)
    }

    @Test
    fun everySelfRegistrableRoleIsAccepted() {
        for (role in RegistrationPolicy.selfRegistrableRoles) {
            assertThat(RegistrationPolicy.initialStatus(role))
                .isInstanceOf(RegistrationDecision.Accepted::class.java)
        }
    }
}
