package com.otli.app.auth.adapters.firestore

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.MerchantStoreDetails
import com.otli.app.auth.domain.ProfileFields
import com.otli.app.auth.domain.Role
import com.otli.app.core.di.OtliFirebase
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Adapter-level proof against the Auth + Firestore emulators (auth-roles spec). */
@RunWith(AndroidJUnit4::class)
class FirestoreAuthRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val auth = OtliFirebase.auth(context)
    private val firestore = OtliFirebase.firestore(context)
    private val repository = FirestoreAuthRepository(auth, firestore)
    private val profile = ProfileFields(displayName = "Test User", phone = "+505 8888 0000")
    private val store = MerchantStoreDetails("Pulperia Test", "+50588880000", 12.2656, -86.5664)

    @Before
    fun signedOut() {
        auth.signOut()
    }

    @After
    fun cleanUp() {
        auth.signOut()
    }

    private fun uniqueEmail() = "user-${UUID.randomUUID()}@otli.test"

    private fun <T> await(block: suspend () -> T): T = runBlocking { withTimeout(30_000) { block() } }

    private fun registerAndReadAccount(role: Role) = await {
        val result = repository.register(
            uniqueEmail(),
            PASSWORD,
            role,
            profile,
            merchantStore = store.takeIf { role == Role.MERCHANT },
        )
        assertThat(result.isSuccess).isTrue()
        val uid = checkNotNull(auth.currentUser).uid
        repository.observeUserDocument(uid).first { it != null }!!
    }

    @Test
    fun customerRegistrationCreatesActiveAccount() {
        val account = registerAndReadAccount(Role.CUSTOMER)
        assertThat(account.role).isEqualTo(Role.CUSTOMER)
        assertThat(account.status).isEqualTo(AccountStatus.ACTIVE)
        assertThat(account.displayName).isEqualTo(profile.displayName)
        assertThat(account.phone).isEqualTo(profile.phone)
    }

    @Test
    fun merchantRegistrationCreatesPendingAccount() {
        val account = registerAndReadAccount(Role.MERCHANT)
        assertThat(account.role).isEqualTo(Role.MERCHANT)
        assertThat(account.status).isEqualTo(AccountStatus.PENDING)
    }

    @Test
    fun merchantRegistrationWritesAPendingClosedMerchantProfileWithItsStoreDetails() = await {
        repository.register(uniqueEmail(), PASSWORD, Role.MERCHANT, profile, store).getOrThrow()
        val uid = checkNotNull(auth.currentUser).uid

        val merchant = firestore.collection("merchants").document(uid).get().await()
        assertThat(merchant.exists()).isTrue()
        assertThat(merchant.getString("name")).isEqualTo("Pulperia Test")
        assertThat(merchant.getString("phone")).isEqualTo("+50588880000")
        assertThat(merchant.getString("description")).isEqualTo("")
        assertThat(merchant.getString("status")).isEqualTo("pending")
        assertThat(merchant.getBoolean("isOpen")).isFalse()
        val location = merchant.get("location") as Map<*, *>
        assertThat(location["lat"]).isEqualTo(12.2656)
        assertThat(location["lng"]).isEqualTo(-86.5664)
    }

    @Test
    fun merchantRegistrationWithoutStoreDetailsFailsAndCreatesNoSession() = await {
        val result = repository.register(uniqueEmail(), PASSWORD, Role.MERCHANT, profile, merchantStore = null)

        assertThat(result.isFailure).isTrue()
        assertThat(auth.currentUser).isNull()
    }

    @Test
    fun customerRegistrationWritesNoMerchantProfile() = await {
        repository.register(uniqueEmail(), PASSWORD, Role.CUSTOMER, profile).getOrThrow()
        val uid = checkNotNull(auth.currentUser).uid

        assertThat(firestore.collection("merchants").document(uid).get().await().exists()).isFalse()
    }

    @Test
    fun courierRegistrationCreatesPendingAccount() {
        val account = registerAndReadAccount(Role.COURIER)
        assertThat(account.role).isEqualTo(Role.COURIER)
        assertThat(account.status).isEqualTo(AccountStatus.PENDING)
    }

    @Test
    fun adminSelfRegistrationIsRejectedAndCreatesNoSession() = await {
        val result = repository.register(uniqueEmail(), PASSWORD, Role.ADMIN, profile)
        assertThat(result.isFailure).isTrue()
        assertThat(auth.currentUser).isNull()
        assertThat(repository.observeAuthState().first()).isNull()
    }

    @Test
    fun loginWithBadCredentialsIsRejectedAndNoSessionResolves() = await {
        val result = repository.login("nobody-${UUID.randomUUID()}@otli.test", "wrong-password")
        assertThat(result.isFailure).isTrue()
        assertThat(repository.observeAuthState().first()).isNull()
    }

    @Test
    fun loginAfterLogoutResolvesSessionForTheSameUser() = await {
        val email = uniqueEmail()
        repository.register(email, PASSWORD, Role.CUSTOMER, profile).getOrThrow()
        val uid = checkNotNull(auth.currentUser).uid
        repository.logout()
        assertThat(repository.observeAuthState().first()).isNull()

        repository.login(email, PASSWORD).getOrThrow()
        val user = repository.observeAuthState().first { it != null }
        assertThat(user?.uid).isEqualTo(uid)
        assertThat(user?.email).isEqualTo(email)
    }

    private companion object {
        const val PASSWORD = "otli-demo-123"
    }
}
