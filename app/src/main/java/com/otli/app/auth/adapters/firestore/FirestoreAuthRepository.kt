package com.otli.app.auth.adapters.firestore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.otli.app.auth.application.AuthRepository
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.AuthUser
import com.otli.app.auth.domain.ProfileFields
import com.otli.app.auth.domain.RegistrationDecision
import com.otli.app.auth.domain.RegistrationPolicy
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.UserAccount
import com.otli.app.core.result.DomainException
import com.otli.app.core.result.suspendRunCatching
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/** Firebase Auth for credentials, `users/{uid}` in Firestore for role and status (ADR-6). */
class FirestoreAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : AuthRepository {

    override fun observeAuthState(): Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.let { AuthUser(it.uid, it.email.orEmpty()) })
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override fun observeUserDocument(uid: String): Flow<UserAccount?> = callbackFlow {
        val registration = firestore.collection(USERS).document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error) else trySend(snapshot?.toUserAccount())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun register(
        email: String,
        password: String,
        role: Role,
        profileFields: ProfileFields,
    ): Result<Unit> = suspendRunCatching {
        val status = when (val decision = RegistrationPolicy.initialStatus(role)) {
            is RegistrationDecision.Accepted -> decision.status
            is RegistrationDecision.Rejected -> throw DomainException(decision.error)
        }
        val user = checkNotNull(auth.createUserWithEmailAndPassword(email, password).await().user)
        try {
            val batch = firestore.batch()
            batch.set(
                firestore.collection(USERS).document(user.uid),
                mapOf(
                    "role" to role.wire(),
                    "status" to status.wire(),
                    "displayName" to profileFields.displayName,
                    "email" to email,
                    "phone" to profileFields.phone,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            batch.commit().await()
        } catch (failure: Throwable) {
            // Do not leave a credential without a profile behind; best effort, and it must
            // finish even when this failure is a cancellation.
            withContext(NonCancellable) { suspendRunCatching { user.delete().await() } }
            auth.signOut()
            throw failure
        }
    }

    override suspend fun login(email: String, password: String): Result<Unit> = suspendRunCatching {
        auth.signInWithEmailAndPassword(email, password).await()
        Unit
    }

    override suspend fun logout() = auth.signOut()

    private fun DocumentSnapshot.toUserAccount(): UserAccount? {
        if (!exists()) return null
        return UserAccount(
            uid = id,
            role = Role.entries.first { it.wire() == getString("role") },
            status = AccountStatus.entries.first { it.wire() == getString("status") },
            displayName = getString("displayName").orEmpty(),
            email = getString("email").orEmpty(),
            phone = getString("phone").orEmpty(),
        )
    }

    private fun Role.wire() = name.lowercase()

    private fun AccountStatus.wire() = name.lowercase()

    private companion object {
        const val USERS = "users"
    }
}
